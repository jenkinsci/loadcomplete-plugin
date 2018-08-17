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

import hudson.FilePath;
import hudson.model.Run;

import java.io.File;
import java.io.IOException;

/**
 * @author Igor Filin
 */
public class Workspace {

    private final FilePath slaveWorkspacePath;
    private final String logId;

    private final FilePath slaveZIPFilePath;
    private final FilePath slavePDFFilePath;
    private final FilePath slaveMHTFilePath;

    private final FilePath masterZIPFilePath;
    private final FilePath masterPDFFilePath;
    private final FilePath masterMHTFilePath;

    private final FilePath slaveErrorFilePath;

    private final FilePath masterLogDirectory;

    public Workspace(Run<?, ?> run, FilePath filePath) throws IOException, InterruptedException {
        this.slaveWorkspacePath = getSlaveWorkspace(filePath);
        this.logId = Long.toString(System.currentTimeMillis() % 10000000);

        String zipName = this.logId + Constants.ZIP_FILE_EXTENSION;
        String mhtName = this.logId + Constants.MHT_FILE_EXTENSION;
        String pdfName = this.logId + Constants.PDF_FILE_EXTENSION;

        slaveZIPFilePath = new FilePath(slaveWorkspacePath, zipName);
        slavePDFFilePath = new FilePath(slaveWorkspacePath, pdfName);
        slaveMHTFilePath = new FilePath(slaveWorkspacePath, mhtName);

        this.masterLogDirectory = getMasterLogDirectory(run);

        masterZIPFilePath = new FilePath(masterLogDirectory, zipName);
        masterPDFFilePath = new FilePath(masterLogDirectory, pdfName);
        masterMHTFilePath = new FilePath(masterLogDirectory, mhtName);

        this.slaveErrorFilePath = new FilePath(slaveWorkspacePath, this.logId + Constants.ERROR_FILE_EXTENSION);
    }

    private FilePath getMasterLogDirectory(Run<?, ?> run) throws IOException, InterruptedException {

        String buildDir = run.getRootDir().getAbsolutePath();
        FilePath masterLogDirectory = new FilePath(new File(buildDir +
                File.separator + Constants.REPORTS_DIRECTORY_NAME));

        masterLogDirectory.mkdirs();
        return masterLogDirectory;
    }

    private FilePath getSlaveWorkspace(FilePath filePath) throws IOException, InterruptedException {

        if (filePath == null) {
            throw new IOException(Messages.LCTestBuilder_WorkspaceNotSpecified());
        }

        filePath.mkdirs();
        return filePath.absolutize();
    }

    public FilePath getSlaveWorkspacePath() {
        return slaveWorkspacePath;
    }

    public String getLogId() {
        return logId;
    }

    public FilePath getSlaveZIPFilePath() {
        return slaveZIPFilePath;
    }

    public FilePath getSlavePDFFilePath() {
        return slavePDFFilePath;
    }


    public FilePath getSlaveMHTFilePath() {
        return slaveMHTFilePath;
    }

    public FilePath getMasterZIPFilePath() {
        return masterZIPFilePath;
    }

    public FilePath getMasterPDFFilePath() {
        return masterPDFFilePath;
    }

    public FilePath getMasterMHTFilePath() {
        return masterMHTFilePath;
    }

    public FilePath getSlaveErrorFilePath() {
        return slaveErrorFilePath;
    }

    public FilePath getMasterLogDirectory() {
        return masterLogDirectory;
    }

}