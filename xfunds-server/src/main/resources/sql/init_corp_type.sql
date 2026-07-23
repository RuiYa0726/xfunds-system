UPDATE fx_customer SET corp_type = CASE
  WHEN customer_type = 'RETAIL' THEN NULL
  WHEN customer_name LIKE '%银行%' OR customer_name LIKE '%证券%' OR customer_name LIKE '%保险%' OR customer_name LIKE '%基金%' THEN 'FINANCIAL_INSTITUTION'
  WHEN customer_name LIKE '%投资%' OR customer_name LIKE '%资本%' OR customer_name LIKE '%控股%' THEN 'INVESTMENT_HOLDING'
  WHEN customer_name LIKE '%贸易%' OR customer_name LIKE '%商务%' OR customer_name LIKE '%进出口%' OR customer_name LIKE '%商贸%' THEN 'TRADING'
  WHEN customer_name LIKE '%制造%' OR customer_name LIKE '%材料%' OR customer_name LIKE '%钢铁%' OR customer_name LIKE '%物业%' OR customer_name LIKE '%集团%' OR customer_name LIKE '%科技%' OR customer_name LIKE '%电子%' OR customer_name LIKE '%信息%' OR customer_name LIKE '%工业%' OR customer_name LIKE '%能源%' OR customer_name LIKE '%建设%' OR customer_name LIKE '%汽车%' OR customer_name LIKE '%环保%' THEN 'MANUFACTURING'
  WHEN customer_name LIKE '%服务%' OR customer_name LIKE '%咨询%' OR customer_name LIKE '%旅游%' OR customer_name LIKE '%物流%' OR customer_name LIKE '%通信%' OR customer_name LIKE '%电商%' OR customer_name LIKE '%商务%' THEN 'SERVICE'
  ELSE 'SME'
END WHERE customer_type = 'CORP';
SELECT corp_type, COUNT(*) AS cnt FROM fx_customer GROUP BY corp_type ORDER BY cnt DESC;
