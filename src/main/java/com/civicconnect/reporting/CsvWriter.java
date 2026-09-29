package com.civicconnect.reporting;

import com.civicconnect.data.model.ServiceRequestRecord;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.List;

/**
 * RFC 4180 CSV for Excel / Power BI. Cells that start with = + - @ are prefixed with an
 * apostrophe so user-entered text cannot run as a spreadsheet formula (CSV injection).
 */
public final class CsvWriter {

    static final String[] HEADER = {
            "Reference", "Title", "Category", "Department", "Status", "Lifecycle group", "Priority",
            "Requester", "Assignee", "Location", "Created", "Due", "Resolved", "Closed",
            "Overdue", "Overdue hours", "Age days", "Resolution hours", "Within SLA"};

    private CsvWriter() { }

    public static void writeRequests(List<ServiceRequestRecord> rows, Writer out) throws IOException {
        writeLine(out, (Object[]) HEADER);
        for (ServiceRequestRecord r : rows) {
            writeLine(out, r.referenceNo(), r.title(), r.categoryName(), r.departmentName(), r.statusName(),
                    r.lifecycleGroup(), r.priority(), r.requesterName(), r.assigneeName(), r.locationText(),
                    r.createdText(), r.dueText(), r.resolvedText(), r.closedText(),
                    r.overdue() ? "Yes" : "No", r.overdueHours(), r.ageDays(), r.resolutionHours(),
                    r.resolvedWithinSla() == null ? "" : (r.resolvedWithinSla() ? "Yes" : "No"));
        }
        out.flush();
    }

    static void writeLine(Writer out, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.write(',');
            out.write(escape(values[i]));
        }
        out.write("\r\n");
    }

    static String escape(Object value) {
        if (value == null) return "";
        String s = value instanceof BigDecimal bd ? bd.toPlainString() : value.toString();
        if (!s.isEmpty() && !(value instanceof Number) && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
