<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="nav" value="dashboard" scope="request"/>
<c:set var="k" value="${snapshot.kpis()}"/>
<c:set var="base" value="${pageContext.request.contextPath}/reports/requests"/>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>CivicConnect Oversight Dashboard</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/static/reporting.css">
</head>
<body>
<%@ include file="header.jspf" %>
<main>
  <h1>Management and oversight dashboard</h1>
  <p class="subtitle">All figures come from one database snapshot taken at ${k.generatedAtText()} (SAST). Refresh the page for the latest data.</p>

  <c:choose>
    <c:when test="${snapshot.reconciled()}">
      <div class="recon ok">Totals reconcile: status, category, lifecycle and ageing breakdowns all add up to ${k.totalRequests()} requests.</div>
    </c:when>
    <c:otherwise>
      <div class="recon bad"><div><strong>Report totals do not reconcile.</strong>
        <ul><c:forEach var="issue" items="${snapshot.reconciliationIssues()}"><li><c:out value="${issue}"/></li></c:forEach></ul></div></div>
    </c:otherwise>
  </c:choose>

  <!-- KPI tiles: each links to the report that lists exactly those requests -->
  <section class="kpis" aria-label="Headline figures">
    <a class="kpi" href="${base}?report=all"><div class="label">Total requests</div><div class="value">${k.totalRequests()}</div><div class="note">${k.createdLast7Days()} logged in last 7 days</div></a>
    <a class="kpi" href="${base}?report=open"><div class="label">Open</div><div class="value">${k.openRequests()}</div><div class="note">${k.unassignedRequests()} not yet assigned</div></a>
    <a class="kpi ${k.overdueRequests() > 0 ? 'alert' : ''}" href="${base}?report=overdue&sort=overdue&dir=desc"><div class="label">Overdue</div><div class="value">${k.overdueRequests()}</div><div class="note">open and past SLA due date</div></a>
    <a class="kpi" href="${base}?report=resolved"><div class="label">Resolved</div><div class="value">${k.resolvedRequests()}</div><div class="note">${k.resolvedLast7Days()} resolved in last 7 days</div></a>
    <a class="kpi" href="${base}?report=closed"><div class="label">Closed</div><div class="value">${k.closedRequests()}</div><div class="note">incl. ${k.rejectedRequests()} rejected</div></a>
    <div class="kpi"><div class="label">Avg. time to resolve</div><div class="value">${k.avgResolutionDaysText()}<span class="muted" style="font-size:14px"> days</span></div><div class="note">${k.avgResolutionHoursText()} hours</div></div>
    <div class="kpi"><div class="label">Resolved within SLA</div><div class="value">${k.slaCompliancePctText()}</div><div class="note">of all resolved requests</div></div>
  </section>

  <div class="grid grid-2 section">
    <section class="card" aria-labelledby="h-status">
      <h2 id="h-status">Requests by status</h2>
      <div class="hbars">
        <c:forEach var="s" items="${snapshot.statusCounts()}">
          <span class="name"><a href="${base}?report=all&status=${s.statusCode()}">${s.displayName()}</a></span>
          <div class="track" title="${s.displayName()}: ${s.requestCount()} requests (${s.overdueCount()} overdue)">
            <div class="bar" style="width:${snapshot.pct(s.requestCount(), snapshot.maxStatusCount())}%"></div>
          </div>
          <span class="num">${s.requestCount()}</span>
        </c:forEach>
      </div>
    </section>

    <section class="card" aria-labelledby="h-age">
      <h2 id="h-age">Ageing of open requests</h2>
      <div class="legend"><span><span class="sw s1"></span>Within SLA</span><span><span class="sw s2"></span>Overdue</span></div>
      <div class="hbars">
        <c:forEach var="b" items="${snapshot.ageing()}">
          <span class="name">${b.label()}</span>
          <div class="track" title="${b.label()}: ${b.openRequests()} open, ${b.overdueRequests()} overdue">
            <c:if test="${b.openRequests() - b.overdueRequests() > 0}">
              <div class="bar" style="width:${snapshot.pct(b.openRequests() - b.overdueRequests(), snapshot.maxAgeingCount())}%"></div>
            </c:if>
            <c:if test="${b.overdueRequests() > 0}">
              <div class="bar s2" style="width:${snapshot.pct(b.overdueRequests(), snapshot.maxAgeingCount())}%"></div>
            </c:if>
          </div>
          <span class="num">${b.openRequests()}</span>
        </c:forEach>
      </div>
    </section>
  </div>

  <section class="card section" aria-labelledby="h-trend">
    <h2 id="h-trend">Requests logged vs resolved per month</h2>
    <div class="legend"><span><span class="sw s1"></span>Logged</span><span><span class="sw s2"></span>Resolved</span></div>
    <div class="cols">
      <c:forEach var="m" items="${snapshot.trend()}">
        <div class="col" title="${m.monthLabel()}: ${m.createdCount()} logged, ${m.resolvedCount()} resolved">
          <div class="bar" style="height:${snapshot.pct(m.createdCount(), snapshot.maxTrendCount())}%"></div>
          <div class="bar s2" style="height:${snapshot.pct(m.resolvedCount(), snapshot.maxTrendCount())}%"></div>
        </div>
      </c:forEach>
    </div>
    <div class="col-labels">
      <c:forEach var="m" items="${snapshot.trend()}"><span>${m.monthLabel()}<br><span class="muted">${m.createdCount()} / ${m.resolvedCount()}</span></span></c:forEach>
    </div>
  </section>

  <section class="card section" aria-labelledby="h-cat">
    <h2 id="h-cat">Statistics by category</h2>
    <div class="table-wrap">
      <table>
        <thead><tr><th>Category</th><th>Department</th><th class="n">SLA (h)</th><th class="n">Total</th><th class="n">Open</th>
          <th class="n">Overdue</th><th class="n">Resolved</th><th class="n">Closed</th><th class="n">Avg. resolve (h)</th><th class="n">Within SLA</th></tr></thead>
        <tbody>
        <c:forEach var="cs" items="${snapshot.categories()}">
          <tr>
            <td><a href="${base}?report=all&category=${cs.categoryId()}">${cs.categoryName()}</a></td>
            <td class="muted">${cs.departmentName()}</td>
            <td class="n">${cs.slaHours()}</td>
            <td class="n">${cs.totalRequests()}</td>
            <td class="n"><a href="${base}?report=open&category=${cs.categoryId()}">${cs.openRequests()}</a></td>
            <td class="n"><c:choose><c:when test="${cs.overdueRequests() > 0}"><a class="overdue-flag" href="${base}?report=overdue&category=${cs.categoryId()}">${cs.overdueRequests()}</a></c:when><c:otherwise>0</c:otherwise></c:choose></td>
            <td class="n">${cs.resolvedRequests()}</td>
            <td class="n">${cs.closedRequests()}</td>
            <td class="n">${cs.avgResolutionHoursText()}</td>
            <td class="n">${cs.slaCompliancePctText()}</td>
          </tr>
        </c:forEach>
        </tbody>
        <tfoot><tr><th>All categories</th><th></th><th></th><th class="n">${k.totalRequests()}</th><th class="n">${k.openRequests()}</th>
          <th class="n">${k.overdueRequests()}</th><th class="n">${k.resolvedRequests()}</th><th class="n">${k.closedRequests()}</th>
          <th class="n">${k.avgResolutionHoursText()}</th><th class="n">${k.slaCompliancePctText()}</th></tr></tfoot>
      </table>
    </div>
  </section>

  <section class="card section" aria-labelledby="h-matrix">
    <h2 id="h-matrix">Requests by category and status</h2>
    <p class="muted" style="margin-top:-6px">Darker cells hold more requests. Select a cell to list those requests.</p>
    <div class="table-wrap">
      <table class="heat">
        <thead><tr><th>Category</th>
          <c:forEach var="col" items="${snapshot.matrix().columns()}"><th class="n">${col.displayName()}</th></c:forEach>
          <th class="n">Total</th></tr></thead>
        <tbody>
        <c:forEach var="row" items="${snapshot.matrix().rows()}">
          <tr><td>${row.categoryName()}</td>
            <c:forEach var="col" items="${snapshot.matrix().columns()}">
              <c:set var="v" value="${row.count(col.statusCode())}"/>
              <td class="cell h${snapshot.heatStep(v)}" title="${row.categoryName()} - ${col.displayName()}: ${v}">
                <a href="${base}?report=all&category=${row.categoryId()}&status=${col.statusCode()}">${v}</a></td>
            </c:forEach>
            <td class="n"><strong>${row.total()}</strong></td></tr>
        </c:forEach>
        </tbody>
        <tfoot><tr><th>Total</th>
          <c:forEach var="col" items="${snapshot.matrix().columns()}"><th class="n">${col.requestCount()}</th></c:forEach>
          <th class="n">${k.totalRequests()}</th></tr></tfoot>
      </table>
    </div>
  </section>
</main>
</body>
</html>
