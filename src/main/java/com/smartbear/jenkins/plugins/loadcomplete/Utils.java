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

import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class Utils {

    private Utils() {
    }

    public static boolean isWindows(VirtualChannel channel, TaskListener listener) {
        try {
            return channel.call(new Callable<Boolean, Exception>() {

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Boolean call() throws Exception {
                    String os = System.getProperty("os.name");
                    if (os != null) {
                        os = os.toLowerCase();
                    }
                    return (os != null && os.contains("windows"));
                }

            });
        } catch (Exception e) {
            LCLog.error(listener, Messages.LCTestBuilder_RemoteCallingFailed(), e);
            return false;
        }
    }

    public static long getSystemTime(VirtualChannel channel, TaskListener listener) {
        try {
            return channel.call(new Callable<Long, Exception>() {

                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    // Stub
                }

                public Long call() throws Exception {
                    return System.currentTimeMillis();
                }

            });
        } catch (Exception e) {
            LCLog.error(listener, Messages.LCTestBuilder_RemoteCallingFailed(), e);
            return 0;
        }
    }

    public static class BusyNodeList {

        private Map<WeakReference<Node>, Semaphore> nodeLocks = new HashMap<WeakReference<Node>, Semaphore>();

        public void lock(Node node, TaskListener listener) throws InterruptedException {
            Semaphore semaphore = null;
            synchronized (this) {
                for (WeakReference<Node> nodeRef : nodeLocks.keySet()) {
                    Node actualNode = nodeRef.get();
                    if (actualNode != null && actualNode == node) {
                        semaphore = nodeLocks.get(nodeRef);
                    }
                }

                if (semaphore == null) {
                    semaphore = new Semaphore(1, true);
                    nodeLocks.put(new WeakReference<Node>(node), semaphore);
                } else {
                    listener.getLogger().println();
                    LCLog.info(listener, Messages.LCTestBuilder_WaitingForNodeRelease());
                }
            }

            semaphore.acquire();
        }

        public void release(Node node) throws InterruptedException {
            Semaphore semaphore = null;
            synchronized (this) {
                for (WeakReference<Node> nodeRef : nodeLocks.keySet()) {
                    Node actualNode = nodeRef.get();
                    if (actualNode != null && actualNode == node) {
                        semaphore = nodeLocks.get(nodeRef);
                    }
                }
            }
            if (semaphore != null) {
                semaphore.release();
            }

            Thread.sleep(200);

            // cleanup the unused items
            synchronized (this) {
                List<WeakReference<Node>> toRemove = new ArrayList<WeakReference<Node>>();

                for (WeakReference<Node> nodeRef : nodeLocks.keySet()) {
                    Node actualNode = nodeRef.get();
                    if (actualNode != null && actualNode == node) {
                        semaphore = nodeLocks.get(nodeRef);
                        if (semaphore.availablePermits() > 0) {
                            toRemove.add(nodeRef);
                        }
                    }
                }

                for (WeakReference<Node> nodeRef : toRemove) {
                    nodeLocks.remove(nodeRef);
                }
            }
        }
    }
}