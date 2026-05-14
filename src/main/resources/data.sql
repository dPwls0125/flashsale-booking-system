-- ============================================================
-- Initial Data for Flash Sale
-- ============================================================

-- 1. 회원 데이터 (member)
INSERT IGNORE INTO member (name, point_balance) VALUES ('아이유', 1000000);
INSERT IGNORE INTO member (name, point_balance) VALUES ('박보검', 500000);
INSERT IGNORE INTO member (name, point_balance) VALUES ('수지', 100000);

-- 2. 상품 데이터 (product)
-- 00시 오픈 초특가 숙소 상품 (10개 한정)
INSERT IGNORE INTO product (name, price, check_in_time, check_out_time, description) 
VALUES ('[선착순 특가] 시그니엘 서울 프리미어 룸', 150000, '15:00:00', '11:00:00', '선착순 10명 한정 초특가!');

-- 3. 상품 재고 데이터 (product_stock)
-- 상품 ID 1번의 재고를 10개로 설정
INSERT IGNORE INTO product_stock (product_id, total_quantity, remaining_quantity) 
VALUES (1, 10, 10);
