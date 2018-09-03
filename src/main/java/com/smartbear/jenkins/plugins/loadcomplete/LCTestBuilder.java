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
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.junit.TestResultAction;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Igor Filin
 */
public class LCTestBuilder extends Builder implements Serializable, SimpleBuildStep {

    private static final String SILENT_MODE_ARG = "/SilentMode";
    private static final String NS_ARG = "/ns";
    private static final String RUN_ARG = "/run";
    private static final String EXIT_ARG = "/exit";
    private static final String EXPORT_LOG_ARG = "/ExportLog:";
    private static final String ERROR_LOG_ARG = "/ErrorLog:";
    private static final String TEST_ARG = "/test:";
    private static final String FORCE_CONVERSION_ARG = "/ForceConversion";

    private static final String DEBUG_FLAG_NAME = "LOADCOMPLETE_PLUGIN_DEBUG";
    private boolean DEBUG = false;

    private String project;
    private String test;
    private String executorVersion;
    private String actionOnWarnings;
    private String actionOnErrors;
    private boolean useTimeout;
    private String timeout;

    private boolean generatePDF;
    private boolean generateMHT;

    public enum BuildStepAction {
        NONE,
        MAKE_UNSTABLE,
        MAKE_FAILED
    }

    private static Utils.BusyNodeList busyNodes = new Utils.BusyNodeList();

    @DataBoundConstructor
    public LCTestBuilder(String project, String test) {
        this.project = project != null ? project : "";
        this.test = test != null ? test : "";
        this.executorVersion = Constants.ANY_CONSTANT;
        this.actionOnWarnings = BuildStepAction.NONE.toString();
        this.actionOnErrors = BuildStepAction.MAKE_UNSTABLE.toString();
        this.useTimeout = false;
        this.timeout = "";

        this.generatePDF = true;
        this.generateMHT = false;
    }

    @DataBoundSetter
    public void setProject(String project) {
        this.project = project;
    }

    public String getProject() {
        return project;
    }

    @DataBoundSetter
    public void setTest(String test) {
        this.test = test;
    }

    public String getTest() {
        return test;
    }

    @DataBoundSetter
    public void setExecutorVersion(String executorVersion) {
        this.executorVersion = executorVersion;
    }

    public String getExecutorVersion() {
        return executorVersion;
    }

    @DataBoundSetter
    public void setActionOnWarnings(String actionOnWarnings) {
        this.actionOnWarnings = actionOnWarnings;
    }

    public String getActionOnWarnings() {
        return actionOnWarnings;
    }

    @DataBoundSetter
    public void setActionOnErrors(String actionOnErrors) {
        this.actionOnErrors = actionOnErrors;
    }

    public String getActionOnErrors() {
        return actionOnErrors;
    }

    @DataBoundSetter
    public void setUseTimeout(boolean useTimeout) {
        this.useTimeout = useTimeout;
    }

    public boolean getUseTimeout() {
        return useTimeout;
    }

    @DataBoundSetter
    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setGeneratePDF(boolean generatePDF) {
        this.generatePDF = generatePDF;
    }

    public boolean getGeneratePDF() {
        return generatePDF;
    }

    @DataBoundSetter
    public void setGenerateMHT(boolean generateMHT) {
        this.generateMHT = generateMHT;
    }

    public boolean getGenerateMHT() {
        return generateMHT;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run,
                        @Nonnull FilePath filePath,
                        @Nonnull Launcher launcher,
                        @Nonnull TaskListener taskListener) throws InterruptedException, IOException {

        Node currentNode = run.getExecutor().getOwner().getNode();
        busyNodes.lock(currentNode, taskListener);

        try {
            performInternal(run, filePath, launcher, taskListener);
        } finally {
            busyNodes.release(currentNode);
        }
    }

    public boolean performInternal(Run<?, ?> run, FilePath filePath, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {

        final PrintStream logger = listener.getLogger();
        logger.println();

        EnvVars env = run.getEnvironment(listener);
        DEBUG = false;
        try {
            DEBUG = Boolean.parseBoolean(env.expand("${" + DEBUG_FLAG_NAME + "}"));
        } catch (Exception e) {
            // Do nothing
        }

        if (DEBUG) {
            LCLog.debug(listener, Messages.LCTestBuilder_Debug_Enabled());
        }

        String testDisplayName;
        try {
            testDisplayName = makeDisplayName(run, listener);
        } catch (Exception e) {
            LCLog.error(listener, Messages.LCTestBuilder_ExceptionOccurred(), e.toString());
            LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return false;
        }

        LCLog.info(listener, Messages.LCTestBuilder_TestStartedMessage(), testDisplayName);

        if (!Utils.isWindows(launcher.getChannel(), listener)) {
            LCLog.error(listener, Messages.LCTestBuilder_NotWindowsOS());
            LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return false;
        }

        // Search for required LC installation

        final LCInstallationsScanner scanner = new LCInstallationsScanner(launcher.getChannel(), listener);
        List<LCInstallation> installations = scanner.getInstallations();

        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append(Messages.LCTestBuilder_FoundedInstallations());
        for (LCInstallation i : installations) {
            msgBuilder.append("\n\t").append(i);
        }

        LCLog.info(listener, msgBuilder.toString());

        final LCInstallation chosenInstallation = scanner.findInstallation(installations, getExecutorVersion());

        if (chosenInstallation == null) {
            LCLog.error(listener, Messages.LCTestBuilder_InstallationNotFound());
            LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return false;
        }

        if (chosenInstallation.compareVersion("4.95", false) < 0) {
            LCLog.error(listener, Messages.LCTestBuilder_UnsupportedVersion());
            LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return false;
        }

        LCLog.info(listener, Messages.LCTestBuilder_ChosenInstallation() + "\n\t" + chosenInstallation);

        // Generating  paths
        final Workspace workspace;
        try {
            workspace = new Workspace(run, filePath);
        } catch (IOException e) {
            LCLog.error(listener, Messages.LCTestBuilder_ExceptionOccurred(), e.toString());
            LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
            return false;
        }

        // Making the command line
        ArgumentListBuilder args = makeCommandLineArgs(run, launcher, listener, workspace, chosenInstallation);

        // Launching and data processing
        final LCReportAction LCReportAction = new LCReportAction(run, workspace.getLogId(), testDisplayName,
                run.getExecutor().getOwner().getNode().getDisplayName());

        int exitCode = -2;
        boolean result = false;

        Proc process = null;
        try {
            LCLog.info(listener, Messages.LCTestBuilder_LaunchingTestRunner());


            long timeout = getTimeoutValue(null, env);
            long startTime = Utils.getSystemTime(launcher.getChannel(), listener);

            Launcher.ProcStarter processStarter = launcher.launch().cmds(args).envs(run.getEnvironment(listener));

            process = processStarter.start();

            if (timeout == -1) {
                exitCode = process.join();
            } else {
                exitCode = process.joinWithTimeout(timeout, TimeUnit.SECONDS, listener);
            }
            process = null;

            long stopTime = Utils.getSystemTime(launcher.getChannel(), listener);

            LCLog.info(listener, Messages.LCTestBuilder_ExitCodeMessage(), exitCode);

            processFiles(listener, workspace, LCReportAction, startTime, stopTime);

            if (exitCode == 0) {
                result = true;
            } else if (exitCode == 1) {
                LCLog.warning(listener, Messages.LCTestBuilder_BuildStepHasWarnings());
                if (actionOnWarnings.equals(BuildStepAction.MAKE_UNSTABLE.name())) {
                    LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsUnstable());
                    run.setResult(Result.UNSTABLE);
                    result = true;
                } else if (actionOnWarnings.equals(BuildStepAction.MAKE_FAILED.name())) {
                    LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
                    run.setResult(Result.FAILURE);
                } else {
                    result = true;
                }
            } else {
                LCLog.warning(listener, Messages.LCTestBuilder_BuildStepHasErrors());
                if (actionOnErrors.equals(BuildStepAction.MAKE_UNSTABLE.name())) {
                    LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsUnstable());
                    run.setResult(Result.UNSTABLE);
                } else if (actionOnErrors.equals(BuildStepAction.MAKE_FAILED.name())) {
                    LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
                    run.setResult(Result.FAILURE);
                }
            }
        } catch (InterruptedException e) {
            // The build has been aborted. Let Jenkins mark it as ABORTED
            throw e;
        } catch (Exception e) {
            LCLog.error(listener, Messages.LCTestBuilder_ExceptionOccurred(),
                    e.getCause() == null ? e.toString() : e.getCause().toString());
            LCLog.info(listener, Messages.LCTestBuilder_MarkingBuildAsFailed());
            run.setResult(Result.FAILURE);
        } finally {
            if (process != null) {
                try {
                    process.kill();
                } catch (Exception e) {
                    // Do nothing
                }
            }

            LCReportAction.setExitCode(exitCode);
            LCReportAction.setResult(result);

            LCSummaryAction currentAction = getOrCreateAction(run);
            currentAction.addReport(LCReportAction);
        }

        LCLog.info(listener, Messages.LCTestBuilder_TestExecutionFinishedMessage(), testDisplayName);
        return true;
    }

    private TestResultAction getTestResultAction(AbstractBuild<?, ?> build) {
        return build.getAction(TestResultAction.class);
    }

    private void processFiles(TaskListener listener, Workspace workspace, LCReportAction testResult,
                              long startTime, long stopTime) throws IOException, InterruptedException {

        boolean hasError = false;
        BufferedReader br = null;

        try {
            if (workspace.getSlaveErrorFilePath().exists()) {
                br = new BufferedReader(new InputStreamReader(workspace.getSlaveErrorFilePath().read(), Charset.forName(Constants.DEFAULT_CHARSET_NAME)));
                String errorString = br.readLine().trim();
                LCLog.warning(listener, Messages.LCTestBuilder_ErrorMessage(), errorString);
                testResult.setError(errorString);
                hasError = true;
            }
        } finally {
            if (br != null) {
                br.close();
            }
            workspace.getSlaveErrorFilePath().delete();
        }

        String extraInfo = "";

        if (!hasError) {
            extraInfo = " " + Messages.LCTestBuilder_CheckLogGenerationOptions();
        }

        if (workspace.getSlaveZIPFilePath().exists()) {
            try {
                workspace.getSlaveZIPFilePath().copyTo(workspace.getMasterZIPFilePath());
                String logFileName = workspace.getMasterZIPFilePath().getName();
                testResult.setZipLogFileName(logFileName);
                LCLogInfo info = new LCLogInfo(startTime, stopTime);
                testResult.setLogInfo(info);
            } finally {
                workspace.getSlaveZIPFilePath().delete();
            }
        }
        else {
            LCLog.warning(listener, Messages.LCTestBuilder_UnableToFindLogFile() + extraInfo,
                    workspace.getSlaveZIPFilePath().getName());

            testResult.setLogInfo(new LCLogInfo(startTime, stopTime));
        }

        if (getGeneratePDF()) {
            if (workspace.getSlavePDFFilePath().exists()) {
                try {
                    workspace.getSlavePDFFilePath().copyTo(workspace.getMasterPDFFilePath());
                    String logFileName = workspace.getMasterPDFFilePath().getName();
                    testResult.setPdfLogFileName(logFileName);
                } finally {
                    workspace.getSlavePDFFilePath().delete();
                }
            } else {
                LCLog.warning(listener, Messages.LCTestBuilder_UnableToFindLogFile() + extraInfo,
                        workspace.getSlavePDFFilePath().getName());
            }
        }

        if (getGenerateMHT()) {
            if (workspace.getSlaveMHTFilePath().exists()) {
                try {
                    workspace.getSlaveMHTFilePath().copyTo(workspace.getMasterMHTFilePath());
                    String logFileName = workspace.getMasterMHTFilePath().getName();
                    testResult.setMhtLogFileName(logFileName);
                } finally {
                    workspace.getSlaveMHTFilePath().delete();
                }
            } else {
                LCLog.warning(listener, Messages.LCTestBuilder_UnableToFindLogFile() + extraInfo,
                        workspace.getSlaveMHTFilePath().getName());
            }
        }
    }

    private String makeDisplayName(Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException {
        StringBuilder builder = new StringBuilder();
        EnvVars env = run.getEnvironment(listener);

        String projectFileName = new FilePath(new File(env.expand(getProject()))).getBaseName();
        builder.append(projectFileName);
        builder.append("/");
        builder.append(env.expand(getTest()));

        return builder.toString();
    }

    private long getTimeoutValue(BuildListener listener, EnvVars env) {
        if (getUseTimeout()) {
            try {
                long timeout = Long.parseLong(env.expand(getTimeout()));
                if (timeout > 0) {
                    return timeout;
                }
            } catch (NumberFormatException e) {
                // Do nothing
            }

            if (listener != null) {
                LCLog.warning(listener, Messages.LCTestBuilder_InvalidTimeoutValue(),
                        env.expand(getTimeout()));
            }
        }
        return -1; // infinite
    }

    private ArgumentListBuilder makeCommandLineArgs(Run<?, ?> run,
                                                    Launcher launcher,
                                                    TaskListener listener,
                                                    Workspace workspace,
                                                    LCInstallation installation) throws IOException, InterruptedException {

        ArgumentListBuilder args = new ArgumentListBuilder();

        FilePath execPath = new FilePath(launcher.getChannel(), installation.getExecutorPath());
        args.add(execPath.getRemote());

        EnvVars env = run.getEnvironment(listener);

        args.add(new FilePath(workspace.getSlaveWorkspacePath(), env.expand(getProject()).replaceAll("\"", "\"\"")));

        args.add(TEST_ARG + env.expand(getTest()).replaceAll("\"", "\"\""));

        args.add(SILENT_MODE_ARG);
        args.add(FORCE_CONVERSION_ARG);
        args.add(NS_ARG);
        args.add(RUN_ARG);
        args.add(EXIT_ARG);

        args.add(ERROR_LOG_ARG + workspace.getSlaveErrorFilePath().getRemote());

        args.add(EXPORT_LOG_ARG + workspace.getSlaveZIPFilePath().getRemote());

        if (getGeneratePDF()) {
            args.add(EXPORT_LOG_ARG + workspace.getSlavePDFFilePath().getRemote());
        }

        if (getGenerateMHT()) {
            args.add(EXPORT_LOG_ARG + workspace.getSlaveMHTFilePath().getRemote());
        }

        return args;
    }

    private LCSummaryAction getOrCreateAction(Run<?, ?> run) {
        LCSummaryAction currentAction = run.getAction(LCSummaryAction.class);
        if (currentAction == null) {
            currentAction = new LCSummaryAction(run);
            run.addAction(currentAction);
        }
        return currentAction;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension @Symbol("loadcompletetest")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder>  {

        public DescriptorImpl() {
            super(LCTestBuilder.class);
            load();
        }

        public String getPluginUrl() {
            return Constants.PLUGIN_URL;
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }

        public FormValidation doCheckProject(@QueryParameter String value) throws IOException, ServletException {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.LCTestBuilder_Descriptor_ValueNotSpecified());
            }

            if (value.contains("\"")) {
                return FormValidation.error(String.format(Messages.LCTestBuilder_Descriptor_InvalidCharacter(), "\""));
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckTest(@QueryParameter String value) throws IOException, ServletException {
            if (value.trim().isEmpty()) {
                return FormValidation.error(Messages.LCTestBuilder_Descriptor_ValueNotSpecified());
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckTimeout(@QueryParameter String value) throws IOException, ServletException {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error(Messages.LCTestBuilder_Descriptor_IsNotNumber());
            }
        }

        public ListBoxModel doFillExecutorVersionItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.LCTestBuilder_Descriptor_LatestTagText(), Constants.ANY_CONSTANT);
            model.add("4", "4");
            return model;
        }

        public ListBoxModel doFillActionOnWarningsItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.BuildStepAction_None(), BuildStepAction.NONE.name());
            model.add(Messages.BuildStepAction_MakeUnstable(), BuildStepAction.MAKE_UNSTABLE.name());
            model.add(Messages.BuildStepAction_MakeFailed(), BuildStepAction.MAKE_FAILED.name());
            return model;
        }

        public ListBoxModel doFillActionOnErrorsItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.BuildStepAction_None(), BuildStepAction.NONE.name());
            model.add(Messages.BuildStepAction_MakeUnstable(), BuildStepAction.MAKE_UNSTABLE.name());
            model.add(Messages.BuildStepAction_MakeFailed(), BuildStepAction.MAKE_FAILED.name());
            return model;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return Messages.LCTestBuilder_DisplayName();
        }
    }

}