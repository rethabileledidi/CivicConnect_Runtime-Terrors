<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="nav" value="" scope="request"/>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${sr.referenceNo()} - CivicConnect</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/static/reporting.css">
</head>
<body>
<%@ include file="header.jspf" %>
<main>
  <p><a href="javascript:history.back()">&larr; Back to report</a></p>
  <h1>${sr.referenceNo()} &middot; <c:out value="${sr.title()}"/></h1>
  <p class="subtitle"><span class="badge ${sr.lifecycleGroup()}">${sr.statusName()}</span>
    <c:if test="${sr.overdue()}"> <span class="overdue-flag">Overdue by ${sr.overdueHours()} hours</span></c:if></p>

  <div class="grid grid-2">
    <section class="card">
      <h2>Request details</h2>
      <dl class="facts">
        <dt>Category</dt><dd>${sr.categoryName()} <span class="muted">(${sr.departmentName()})</span></dd>
        <dt>Priority</dt><dd>${sr.priority()}</dd>
        <dt>Location</dt><dd><c:out value="${sr.locationText()}"/></dd>
        <dt>Description</dt><dd><c:out value="${sr.description()}"/></dd>
        <dt>Requester</dt><dd><c:out value="${sr.requesterName()}"/></dd>
        <dt>Assignee</dt><dd><c:out value="${sr.assigneeName() != null ? sr.assigneeName() : 'Unassigned'}"/></dd>
        <dt>Logged</dt><dd>${sr.createdText()}</dd>
        <dt>Due (SLA)</dt><dd>${sr.dueText()}</dd>
        <dt>Resolved</dt><dd>${sr.resolvedText()}<c:if test="${sr.resolutionHours() != null}"> <span class="muted">(${sr.resolutionHours()} h, ${sr.resolvedWithinSla() ? 'within SLA' : 'SLA missed'})</span></c:if></dd>
        <dt>Closed</dt><dd>${sr.closedText()}</dd>
        <dt>Age</dt><dd>${sr.ageDays()} days</dd>
        <dt>Record version</dt><dd>${sr.version()}</dd>
      </dl>
    </section>
    <section class="card">
      <h2>Audit trail (${history.size()} entries)</h2>
      <ol class="timeline">
        <c:forEach var="h" items="${history}">
          <li><strong>${h.fromStatusName() != null ? h.fromStatusName() : 'New'} &rarr; ${h.toStatusName()}</strong><br>
            <span class="muted">${h.changedAtText()} &middot; <c:out value="${h.changedByName()}"/> (${h.changedByRole()})</span>
            <c:if test="${h.assigneeName() != null}"><br><span class="muted">Assignee: <c:out value="${h.assigneeName()}"/></span></c:if>
            <c:if test="${h.note() != null}"><br><c:out value="${h.note()}"/></c:if></li>
        </c:forEach>
      </ol>
    </section>
  </div>
</main>
</body>
</html>
