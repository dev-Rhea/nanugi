-- JMeter 부하 테스트용 데이터. JUnit 처리량 테스트와 같은 조건으로 맞춘다.
-- 회원 1명(id=1) + 지출 기록 1,000건(AnalyticsQueryThroughputTest와 동일한 분포)
INSERT INTO members (id, name, nickname, email, kakao_id, role)
VALUES (1, '부하테스트 유저', 'loadtest_jmeter', 'loadtest@example.com', 'kakao_loadtest_jmeter', 'USER')
ON CONFLICT (id) DO NOTHING;

DELETE FROM spending_records WHERE member_id = 1;

INSERT INTO spending_records
    (member_id, type, source_id, title, total_amount, amount_per_person,
     category, participant_count, status, completed_at, created_at)
SELECT 1,
       CASE WHEN i % 2 = 0 THEN 'SETTLEMENT' ELSE 'GROUP_BUY' END,
       i + 1,
       '지출 기록 #' || i,
       ((i % 10 + 1) * 5000) * 3,
       (i % 10 + 1) * 5000,
       (ARRAY['식비', '교통', '쇼핑', '오락', '의료'])[i % 5 + 1],
       3,
       'COMPLETED',
       now() - make_interval(days => i % 30),
       now()
FROM generate_series(0, 999) AS i;
