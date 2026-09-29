-- =============================================================================
-- CivicConnect | V4 - SAMPLE DATA (development / demo / test ONLY - never production)
-- Owner: Person 3 (Database & Management Reporting Module)
--
-- Generates fictional users and ~180 requests spread over the last ~6 months. Every
-- request walks a realistic lifecycle and every status change writes a history row,
-- so the data satisfies the same integrity rules as real application data
-- (the deferred status/history trigger checks it at COMMIT).
-- Deterministic: setseed() makes the same data on every run (relative to now()).
-- =============================================================================

SET search_path = civic;

-- ---------------------------------------------------------------------------
-- Users (fictional)
-- ---------------------------------------------------------------------------
INSERT INTO app_user (email, full_name, role_code, department_id) VALUES
    ('admin@civicconnect.example',       'System Admin',        'ADMIN',       NULL),
    ('n.mokoena@civicconnect.example',   'Naledi Mokoena',      'MANAGER',     NULL),
    ('j.vandermerwe@civicconnect.example','Johan van der Merwe','MANAGER',     NULL),
    ('t.dlamini@civicconnect.example',   'Thabo Dlamini',       'COORDINATOR', NULL),
    ('a.naidoo@civicconnect.example',    'Anika Naidoo',        'COORDINATOR', NULL);

INSERT INTO app_user (email, full_name, role_code, department_id)
SELECT v.email, v.full_name, 'STAFF', d.department_id
FROM (VALUES
    ('s.khumalo@civicconnect.example',  'Sipho Khumalo',     'Roads and Stormwater'),
    ('l.botha@civicconnect.example',    'Lerato Botha',      'Roads and Stormwater'),
    ('k.mahlangu@civicconnect.example', 'Kagiso Mahlangu',   'Water and Sanitation'),
    ('p.pillay@civicconnect.example',   'Priya Pillay',      'Water and Sanitation'),
    ('m.nkosi@civicconnect.example',    'Mpho Nkosi',        'Electricity'),
    ('r.smith@civicconnect.example',    'Ruan Smith',        'Electricity'),
    ('z.mthembu@civicconnect.example',  'Zanele Mthembu',    'Waste Management'),
    ('b.molefe@civicconnect.example',   'Bongani Molefe',    'Parks and Community Services')
) AS v(email, full_name, dept)
JOIN department d ON d.name = v.dept;

INSERT INTO app_user (email, full_name, role_code)
SELECT 'resident' || g || '@mail.example',
       (ARRAY['Ayanda','Lindiwe','Pieter','Fatima','Tshepo','Nomsa','Kyle','Refilwe','Musa','Chantal',
              'Themba','Palesa','Imran','Dineo','Werner'])[1 + (g - 1) % 15]
       || ' ' ||
       (ARRAY['Zulu','Mabena','Jacobs','Adams','Sithole','Ndlovu','Moodley','Maseko','Venter','Radebe'])[1 + (g * 7) % 10],
       'RESIDENT'
FROM generate_series(1, 30) AS g;

-- ---------------------------------------------------------------------------
-- Helper: apply one status change + its history row (mirrors what the Java
-- ServiceRequestRepository.changeStatus() does in a single transaction).
-- ---------------------------------------------------------------------------
CREATE FUNCTION pg_temp.cc_step(p_request BIGINT, p_to VARCHAR, p_at TIMESTAMPTZ,
                                p_by BIGINT, p_assignee BIGINT, p_note TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE
    v_from VARCHAR(20);
BEGIN
    SELECT status_code INTO v_from FROM civic.service_request WHERE request_id = p_request;

    UPDATE civic.service_request
       SET status_code      = p_to,
           assignee_id      = coalesce(p_assignee, assignee_id),
           resolved_at      = CASE WHEN p_to = 'RESOLVED' THEN p_at
                                   WHEN p_to = 'REOPENED' THEN NULL ELSE resolved_at END,
           resolution_notes = CASE WHEN p_to = 'RESOLVED' THEN coalesce(p_note, 'Resolved on site')
                                   WHEN p_to = 'REOPENED' THEN NULL ELSE resolution_notes END,
           closed_at        = CASE WHEN p_to IN ('CLOSED', 'REJECTED') THEN p_at ELSE closed_at END,
           version          = version + 1
     WHERE request_id = p_request;

    INSERT INTO civic.request_status_history (request_id, from_status, to_status, changed_by, changed_at, assignee_id, note)
    VALUES (p_request, v_from, p_to, p_by, p_at,
            (SELECT assignee_id FROM civic.service_request WHERE request_id = p_request), p_note);
END;
$$;

-- ---------------------------------------------------------------------------
-- Requests
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    titles CONSTANT TEXT[][] := ARRAY[
        ['POTHOLE',     'Large pothole on main road',        'Deep pothole causing tyre damage near the intersection.'],
        ['STORMWATER',  'Stormwater drain blocked',          'Drain blocked with debris; street floods when it rains.'],
        ['WATER_LEAK',  'Burst water pipe on pavement',      'Clean water running down the street for several hours.'],
        ['SEWER',       'Sewage overflowing from manhole',   'Manhole overflowing, strong smell in the area.'],
        ['POWER',       'No electricity in the street',      'Whole street without power since this morning.'],
        ['STREETLIGHT', 'Streetlight not working',           'Streetlight outside house has been off for a week.'],
        ['WASTE',       'Refuse not collected',              'Bins were not collected on the scheduled day.'],
        ['DUMPING',     'Illegal dumping at open field',     'Building rubble and household waste dumped on open land.'],
        ['PARKS',       'Broken swings at community park',   'Two swings broken, sharp edges are a danger to children.']
    ];
    suburbs CONSTANT TEXT[] := ARRAY['Soshanguve','Mamelodi','Hatfield','Centurion','Atteridgeville',
                                     'Pretoria CBD','Garsfontein','Akasia','Laudium','Eersterust'];
    priorities CONSTANT TEXT[] := ARRAY['LOW','MEDIUM','MEDIUM','MEDIUM','HIGH','HIGH','URGENT'];

    i            INT;
    t            INT;
    v_cat        RECORD;
    v_req        BIGINT;
    v_requester  BIGINT;
    v_coord      BIGINT;
    v_staff      BIGINT;
    v_created    TIMESTAMPTZ;
    v_at         TIMESTAMPTZ;
    v_limit      TIMESTAMPTZ := now() - interval '5 minutes';
    v_stall_at   INT;     -- 0 = never stalls; 1..3 = stops at SUBMITTED/ASSIGNED/IN_PROGRESS
    v_outcome    FLOAT;
BEGIN
    PERFORM setseed(0.381);

    FOR i IN 1..180 LOOP
        t := 1 + floor(random() * 9)::int;
        SELECT * INTO v_cat FROM civic.request_category WHERE code = titles[t][1];

        -- More recent requests are more common (skewed towards the last few weeks)
        v_created := now() - (power(random(), 1.6) * interval '175 days') - interval '1 hour';
        SELECT user_id INTO v_requester FROM civic.app_user WHERE role_code = 'RESIDENT' ORDER BY random() LIMIT 1;
        SELECT user_id INTO v_coord     FROM civic.app_user WHERE role_code = 'COORDINATOR' ORDER BY random() LIMIT 1;
        SELECT user_id INTO v_staff     FROM civic.app_user
         WHERE role_code = 'STAFF' AND department_id = v_cat.department_id ORDER BY random() LIMIT 1;

        INSERT INTO civic.service_request (title, description, category_id, priority, requester_id,
                                           location_text, created_at, updated_at)
        VALUES (titles[t][2] || ' - ' || suburbs[1 + floor(random() * 10)::int],
                titles[t][3], v_cat.category_id,
                priorities[1 + floor(random() * 7)::int], v_requester,
                (10 + floor(random() * 250)::int) || ' ' ||
                (ARRAY['Church St','Main Rd','Mandela Dr','Jacaranda Ave','Station Rd','Park Lane'])[1 + floor(random() * 6)::int]
                || ', ' || suburbs[1 + floor(random() * 10)::int],
                v_created, v_created)
        RETURNING request_id INTO v_req;

        INSERT INTO civic.request_status_history (request_id, from_status, to_status, changed_by, changed_at, note)
        VALUES (v_req, NULL, 'SUBMITTED', v_requester, v_created, 'Request logged by resident');

        v_stall_at := CASE WHEN random() < 0.22 THEN 1 + floor(random() * 3)::int ELSE 0 END;
        v_outcome  := random();

        -- 7% are rejected at triage (duplicates / out of scope)
        IF v_outcome < 0.07 THEN
            v_at := v_created + (2 + random() * 30) * interval '1 hour';
            IF v_at < v_limit THEN
                PERFORM pg_temp.cc_step(v_req, 'REJECTED', v_at, v_coord, NULL, 'Duplicate of an existing request');
            END IF;
            CONTINUE;
        END IF;
        CONTINUE WHEN v_stall_at = 1;

        -- Assigned
        v_at := v_created + (1 + random() * 20) * interval '1 hour';
        CONTINUE WHEN v_at >= v_limit;
        PERFORM pg_temp.cc_step(v_req, 'ASSIGNED', v_at, v_coord, v_staff, 'Assigned to field team');
        CONTINUE WHEN v_stall_at = 2;

        -- In progress
        v_at := v_at + (1 + random() * 36) * interval '1 hour';
        CONTINUE WHEN v_at >= v_limit;
        PERFORM pg_temp.cc_step(v_req, 'IN_PROGRESS', v_at, v_staff, NULL, 'Work started on site');
        CONTINUE WHEN v_stall_at = 3;

        -- Resolved: between 30% and 170% of the SLA (so some breach the SLA)
        v_at := greatest(v_at + interval '1 hour',
                         v_created + (0.3 + random() * 1.4) * v_cat.sla_hours * interval '1 hour');
        CONTINUE WHEN v_at >= v_limit;
        PERFORM pg_temp.cc_step(v_req, 'RESOLVED', v_at, v_staff, NULL, 'Repair completed');

        -- 10% are reopened by the resident, then worked and resolved again
        IF random() < 0.10 THEN
            v_at := v_at + (6 + random() * 48) * interval '1 hour';
            CONTINUE WHEN v_at >= v_limit;
            PERFORM pg_temp.cc_step(v_req, 'REOPENED', v_at, v_requester, NULL, 'Resident reports problem has returned');
            v_at := v_at + (12 + random() * 72) * interval '1 hour';
            CONTINUE WHEN v_at >= v_limit;
            PERFORM pg_temp.cc_step(v_req, 'RESOLVED', v_at, v_staff, NULL, 'Follow-up repair completed');
        END IF;

        -- Closed after confirmation (about 3 in 4 resolved requests)
        IF random() < 0.75 THEN
            v_at := v_at + (24 + random() * 120) * interval '1 hour';
            CONTINUE WHEN v_at >= v_limit;
            PERFORM pg_temp.cc_step(v_req, 'CLOSED', v_at, v_coord, NULL, 'Closed after resident confirmation');
        END IF;
    END LOOP;
END;
$$;
