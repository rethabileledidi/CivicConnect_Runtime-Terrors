-- =============================================================================
-- CivicConnect | V3 - Reference data (required in every environment)
-- Owner: Person 3 (Database & Management Reporting Module)
-- =============================================================================

SET search_path = civic;

INSERT INTO role (role_code, display_name, description) VALUES
    ('RESIDENT',    'Resident',            'Member of the public who logs service requests'),
    ('STAFF',       'Field / Service Staff','Municipal staff member who works on assigned requests'),
    ('COORDINATOR', 'Coordinator',         'Triages, assigns and re-assigns requests'),
    ('MANAGER',     'Manager',             'Oversight: management dashboard and reports'),
    ('ADMIN',       'System Administrator','Maintains reference data and user accounts');

-- lifecycle_group is the single source for the Open / Resolved / Closed reports.
INSERT INTO request_status (status_code, display_name, lifecycle_group, sort_order, is_terminal) VALUES
    ('SUBMITTED',   'Submitted',   'OPEN',     1, FALSE),
    ('ASSIGNED',    'Assigned',    'OPEN',     2, FALSE),
    ('IN_PROGRESS', 'In Progress', 'OPEN',     3, FALSE),
    ('REOPENED',    'Reopened',    'OPEN',     4, FALSE),
    ('RESOLVED',    'Resolved',    'RESOLVED', 5, FALSE),
    ('CLOSED',      'Closed',      'CLOSED',   6, TRUE),
    ('REJECTED',    'Rejected',    'CLOSED',   7, TRUE);

INSERT INTO department (name, contact_email) VALUES
    ('Roads and Stormwater',       'roads@civicconnect.example'),
    ('Water and Sanitation',       'water@civicconnect.example'),
    ('Electricity',                'electricity@civicconnect.example'),
    ('Waste Management',           'waste@civicconnect.example'),
    ('Parks and Community Services','parks@civicconnect.example');

INSERT INTO request_category (code, name, description, department_id, sla_hours)
SELECT v.code, v.name, v.description, d.department_id, v.sla_hours
FROM (VALUES
    ('POTHOLE',     'Potholes and Road Damage', 'Potholes, cracked or damaged road surfaces', 'Roads and Stormwater',        120),
    ('STORMWATER',  'Blocked Stormwater Drain', 'Blocked drains and flooding',                'Roads and Stormwater',         72),
    ('WATER_LEAK',  'Water Leak / Burst Pipe',  'Burst pipes, leaks and no-water reports',    'Water and Sanitation',         48),
    ('SEWER',       'Sewer Blockage',           'Sewer overflows and blockages',              'Water and Sanitation',         48),
    ('POWER',       'Electricity Outage',       'Area or street power outages',               'Electricity',                  24),
    ('STREETLIGHT', 'Streetlight Fault',        'Streetlights off, flickering or damaged',    'Electricity',                 168),
    ('WASTE',       'Missed Refuse Collection', 'Refuse bins not collected on schedule',      'Waste Management',             72),
    ('DUMPING',     'Illegal Dumping',          'Illegal dumping on public land',             'Waste Management',            168),
    ('PARKS',       'Parks and Public Spaces',  'Damaged park facilities, overgrown grass',   'Parks and Community Services', 240)
) AS v(code, name, description, dept, sla_hours)
JOIN department d ON d.name = v.dept;
