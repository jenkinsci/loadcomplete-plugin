/*
 * The MIT License
 *
 * Copyright (c) 2018, SmartBear Software
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.smartbear.jenkins.plugins.loadcomplete;

import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.util.jna.JnaException;
import hudson.util.jna.RegistryKey;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Igor Filin
 */
public class LCInstallationsScanner implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int REGISTRY_KEY_WOW64_32KEY = 0x0200;
    private static final int REGISTRY_KEY_READ = 0x20019;

    private final VirtualChannel channel;
    private final TaskListener listener;

    public LCInstallationsScanner(VirtualChannel channel, TaskListener listener) {
        this.channel = channel;
        this.listener = listener;
    }

    private static class ScannerCallable implements Callable<List<LCInstallation>, Exception>, Serializable {

        private static final long serialVersionUID = 1L;
        private static final String registryKey = "SOFTWARE\\SmartBear\\";

        ScannerCallable() {

        }

        public List<LCInstallation> call() throws Exception {
            List<LCInstallation> result = new ArrayList<LCInstallation>();
            scanForInstallations(result, "LoadComplete");
            return result;
        }

        private void scanForInstallations(List<LCInstallation> result, String executor) {

            String key = registryKey + executor + "\\";
            RegistryKey executorKey = null;

            try {
                executorKey = RegistryKey.LOCAL_MACHINE.open(key, REGISTRY_KEY_READ | REGISTRY_KEY_WOW64_32KEY);
                Collection<String> installKeys = executorKey.getSubKeys();

                if (installKeys != null && installKeys.size() > 0) {
                    for (String versionKey : installKeys) {
                        if (!versionKey.matches("^[0-9]{1,3}([.][0-9]{1,5})+$")) {
                            continue;
                        }

                        RegistryKey setupKey = null;
                        try {
                            setupKey = executorKey.open(versionKey + "\\Setup\\", REGISTRY_KEY_READ | REGISTRY_KEY_WOW64_32KEY);
                            String path = setupKey.getStringValue("Product path");
                            String version = setupKey.getStringValue("Version");

                            if (version == null || !version.matches("^[0-9]{1,3}([.][0-9]{1,5})+$")) {
                                continue;
                            }

                            if (path != null && !path.isEmpty()) {
                                String executorPath = path + "x64\\bin\\" + executor + ".exe";

                                if (new File(executorPath).exists()) {
                                    result.add(new LCInstallation(executorPath, version));
                                } else {
                                    executorPath = path + "bin\\" + executor + ".exe";
                                    if (new File(executorPath).exists()) {
                                        result.add(new LCInstallation(executorPath, version));
                                    }
                                }
                            }
                        } catch (JnaException e) {
                            // Do nothing
                        } finally {
                            if (setupKey != null)
                                setupKey.dispose();
                        }
                    }
                }
            } catch (JnaException e) {
                // Do nothing
            } finally {
                if (executorKey != null) {
                    executorKey.dispose();
                }
            }
        }

        @Override
        public void checkRoles(RoleChecker roleChecker) throws SecurityException {
            // Stub
        }

    }

    public List<LCInstallation> getInstallations() {
        List<LCInstallation> result = null;

        try {
            result = channel.call(new ScannerCallable());
        } catch (Exception e) {
            LCLog.error(listener, Messages.LCTestBuilder_RemoteCallingFailed(), e.getCause().getMessage());
        }

        return result != null ? result : new LinkedList<LCInstallation>();
    }

    public LCInstallation findInstallation(List<LCInstallation> installations, String executorVersion) {
        LCInstallation result = null;

        for (LCInstallation installation : installations) {
            if (!executorVersion.equals(Constants.ANY_CONSTANT) &&
                    installation.compareVersion(executorVersion, true) != 0) {
                continue;
            }

            if (result == null) {
                result = installation;
                continue;
            }

            if (installation.compareVersion(result.getVersion(), false) > 0) {
                result = installation;
                continue;
            }
        }

        return result;
    }

}