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

import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Run;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.File;
import java.util.*;

/**
 * @author Igor Filin
 */
@ExportedBean
public class LCSummaryAction implements Action {

    private final Run<?, ?> run;

    private LinkedHashMap<String, LCReportAction> reports = new LinkedHashMap<String, LCReportAction>();

    private ArrayList<LCReportAction> reportsOrder = new ArrayList<LCReportAction>();
    private final LCDynamicReportAction dynamic;

    LCSummaryAction(Run<?, ?> run) {
        this.run = run;
        String buildDir = run.getRootDir().getAbsolutePath();
        String reportsPath = buildDir + File.separator + Constants.REPORTS_DIRECTORY_NAME + File.separator;
        dynamic = new LCDynamicReportAction(reportsPath);
    }

    public String getIconFileName() {
        return "/plugin/" + Constants.PLUGIN_URL + "/images/lc-48x48.png";
    }

    public String getDisplayName() {
        return Messages.TcSummaryAction_DisplayName();
    }

    public String getUrlName() {
        return Constants.PLUGIN_URL;
    }

    public Run<?, ?> getRun() {
        return run;
    }

    public void addReport(LCReportAction report) {
        if (!reports.containsValue(report)) {
            report.setParent(this);
            reports.put(report.getId(), report);
            reportsOrder.add(report);
        }
    }

    @Exported(name="reports", inline = true)
    public ArrayList<LCReportAction> getReportsOrder() {
        return reportsOrder;
    }

    public HashMap<String, LCReportAction> getReports() {
        return reports;
    }

    public LCReportAction getNextReport(LCReportAction report) {
        if (report == null || !reportsOrder.contains(report)) {
            return null;
        }
        int index = reportsOrder.indexOf(report);
        if (index + 1 >= reportsOrder.size()) {
            return null;
        }
        return reportsOrder.get(index + 1);
    }

    public LCReportAction getPreviousReport(LCReportAction report) {
        if (report == null || !reportsOrder.contains(report)) {
            return null;
        }
        int index = reportsOrder.indexOf(report);
        if (index <= 0) {
            return null;
        }
        return reportsOrder.get(index - 1);
    }

    public LCDynamicReportAction getDynamic() {
        return dynamic;
    }

    public String getPluginUrl() {
        return Constants.PLUGIN_URL;
    }

    public Api getApi() {
        return new Api(this);
    }

}