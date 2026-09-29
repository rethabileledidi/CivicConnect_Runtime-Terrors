<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="nav" value="${criteria.reportType.param()}" scope="request"/>
<c:set var="base" value="${pageContext.request.contextPath}/reports/requests"/>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${criteria.reportType.title()} - CivicConnect</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/static/reporting.css">
</head>
<body>
<%@ include file="header.jspf" %>
<main>
  <h1>${criteria.reportType.title()}</h1>
  <p class="subtitle">${criteria.reportType.description()}</p>

  <div class="tabs" role="tablist">
    <a href="${base}?report=open"     class="${nav == 'open' ? 'active' : ''}">Open</a>
    <a href="${base}?report=overdue&sort=overdue&dir=desc" class="${nav == 'overdue' ? 'active' : ''}">Overdue</a>
    <a href="${base}?report=resolved" class="${nav == 'resolved' ? 'active' : ''}">Resolved</a>
    <a href="${base}?report=closed"   class="${nav == 'closed' ? 'active' : ''}">Closed</a>
    <a href="${base}?report=all"      class="${nav == 'all' ? 'active' : ''}">All</a>
  </div>

  <section class="card">
    <form class="filters" method="get" action="${base}">
      <input type="hidden" name="report" value="${criteria.reportType.param()}">
      <input type="hidden" name="sort" value="${criteria.sortField.param()}">
      <input type="hidden" name="dir" value="${criteria.ascending ? 'asc' : 'desc'}">
      <label>Search
        <input type="search" name="q" value="<c:out value='${criteria.text}'/>" placeholder="Reference, title, location, person">
      </label>
      <label>Status
        <select name="status" multiple>
          <c:forEach var="o" items="${options.statuses()}">
            <option value="${o.value()}" ${criteria.hasStatus(o.value()) ? 'selected' : ''}>${o.label()}</option>
          </c:forEach>
        </select>
      </label>
      <label>Category
        <select name="category" multiple>
          <c:forEach var="o" items="${options.categories()}">
            <option value="${o.value()}" ${criteria.hasCategory(o.value()) ? 'selected' : ''}>${o.label()}</option>
          </c:forEach>
        </select>
      </label>
      <label>Priority
        <select name="priority" multiple>
          <c:forEach var="p" items="${['URGENT','HIGH','MEDIUM','LOW']}">
            <option value="${p}" ${criteria.hasPriority(p) ? 'selected' : ''}>${p}</option>
          </c:forEach>
        </select>
      </label>
      <label>Assigned to
        <select name="assignee">
          <option value="">Anyone</option>
          <option value="none" ${criteria.unassignedOnly ? 'selected' : ''}>Unassigned</option>
          <c:forEach var="o" items="${options.assignees()}">
            <option value="${o.value()}" ${criteria.assigneeId != null && criteria.assigneeId.toString() == o.value() ? 'selected' : ''}><c:out value="${o.label()}"/></option>
          </c:forEach>
        </select>
      </label>
      <label>Logged from <input type="date" name="from" value="${criteria.createdFromText()}"></label>
      <label>to <input type="date" name="to" value="${criteria.createdToText()}"></label>
      <c:if test="${criteria.reportType.param() != 'overdue'}">
        <label class="check"><input type="checkbox" name="overdue" value="1" ${criteria.overdueOnly ? 'checked' : ''}> Overdue only</label>
      </c:if>
      <button type="submit">Apply</button>
      <a href="${base}?report=${criteria.reportType.param()}">Clear</a>
    </form>
  </section>

  <div class="resultbar">
    <span>
      <c:choose>
        <c:when test="${result.totalCount() == 0}">No requests match these filters.</c:when>
        <c:otherwise>Showing ${result.firstRow()}&ndash;${result.lastRow()} of <strong>${result.totalCount()}</strong> requests</c:otherwise>
      </c:choose>
    </span>
    <a href="${links.csv()}">Download CSV (Excel / Power BI)</a>
  </div>

  <section class="card">
    <div class="table-wrap">
      <table>
        <thead><tr>
          <th><a href="${links.sort('reference')}">Reference<span class="ind ${links.indicator('reference')}"></span></a></th>
          <th><a href="${links.sort('title')}">Title<span class="ind ${links.indicator('title')}"></span></a></th>
          <th><a href="${links.sort('category')}">Category<span class="ind ${links.indicator('category')}"></span></a></th>
          <th><a href="${links.sort('status')}">Status<span class="ind ${links.indicator('status')}"></span></a></th>
          <th><a href="${links.sort('priority')}">Priority<span class="ind ${links.indicator('priority')}"></span></a></th>
          <th><a href="${links.sort('assignee')}">Assignee<span class="ind ${links.indicator('assignee')}"></span></a></th>
          <th><a href="${links.sort('created')}">Logged<span class="ind ${links.indicator('created')}"></span></a></th>
          <th><a href="${links.sort('due')}">Due<span class="ind ${links.indicator('due')}"></span></a></th>
          <th class="n"><a href="${links.sort('age')}">Age (days)<span class="ind ${links.indicator('age')}"></span></a></th>
          <th class="n"><a href="${links.sort('overdue')}">Overdue by (h)<span class="ind ${links.indicator('overdue')}"></span></a></th>
        </tr></thead>
        <tbody>
        <c:forEach var="r" items="${result.items()}">
          <tr>
            <td><a href="${pageContext.request.contextPath}/reports/request?ref=${r.referenceNo()}">${r.referenceNo()}</a></td>
            <td><c:out value="${r.title()}"/><br><span class="muted"><c:out value="${r.locationText()}"/></span></td>
            <td>${r.categoryName()}</td>
            <td><span class="badge ${r.lifecycleGroup()}">${r.statusName()}</span></td>
            <td class="prio-${r.priority()}">${r.priority()}</td>
            <td><c:choose><c:when test="${r.assigneeName() != null}"><c:out value="${r.assigneeName()}"/></c:when><c:otherwise><span class="muted">Unassigned</span></c:otherwise></c:choose></td>
            <td>${r.createdText()}</td>
            <td>${r.dueText()}</td>
            <td class="n">${r.ageDays()}</td>
            <td class="n"><c:if test="${r.overdue()}"><span class="overdue-flag">${r.overdueHours()}</span></c:if></td>
          </tr>
        </c:forEach>
        </tbody>
      </table>
    </div>
  </section>

  <div class="resultbar">
    <span class="muted">Page ${result.page()} of ${result.totalPages()}</span>
    <nav class="pager" aria-label="Pages">
      <c:choose><c:when test="${result.hasPrevious()}"><a href="${links.page(result.page() - 1)}">&larr; Previous</a></c:when><c:otherwise><span class="disabled">&larr; Previous</span></c:otherwise></c:choose>
      <c:choose><c:when test="${result.hasNext()}"><a href="${links.page(result.page() + 1)}">Next &rarr;</a></c:when><c:otherwise><span class="disabled">Next &rarr;</span></c:otherwise></c:choose>
    </nav>
  </div>
</main>
</body>
</html>
