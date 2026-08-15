INSERT INTO rubric_template (id, created_at, updated_at, subject_id, criteria_name, weight, description)
SELECT '11111111-1111-1111-1111-111111111111', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, 'Hoàn thành & Chất lượng', 25, 'Làm đúng, đủ task được giao; code/chức năng chạy ổn định, ít lỗi.'
WHERE NOT EXISTS (
    SELECT 1 FROM rubric_template WHERE id = '11111111-1111-1111-1111-111111111111'
);

INSERT INTO rubric_template (id, created_at, updated_at, subject_id, criteria_name, weight, description)
SELECT '22222222-2222-2222-2222-222222222222', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, 'Tiến độ & Quy trình', 25, 'Đáp ứng đúng deadline; đẩy/merge code kịp thời, không làm kẹt tiến độ chung.'
WHERE NOT EXISTS (
    SELECT 1 FROM rubric_template WHERE id = '22222222-2222-2222-2222-222222222222'
);

INSERT INTO rubric_template (id, created_at, updated_at, subject_id, criteria_name, weight, description)
SELECT '33333333-3333-3333-3333-333333333333', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, 'Giao tiếp & Hỗ trợ', 25, 'Dễ liên lạc; chủ động phối hợp và sẵn sàng giúp đỡ đồng đội.'
WHERE NOT EXISTS (
    SELECT 1 FROM rubric_template WHERE id = '33333333-3333-3333-3333-333333333333'
);

INSERT INTO rubric_template (id, created_at, updated_at, subject_id, criteria_name, weight, description)
SELECT '44444444-4444-4444-4444-444444444444', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL, 'Thái độ & Xử lý sự cố', 25, 'Chịu trách nhiệm với công việc được giao; xử lý sự cố kịp thời và hiệu quả, cởi mở tiếp thu góp ý.'
WHERE NOT EXISTS (
    SELECT 1 FROM rubric_template WHERE id = '44444444-4444-4444-4444-444444444444'
);
