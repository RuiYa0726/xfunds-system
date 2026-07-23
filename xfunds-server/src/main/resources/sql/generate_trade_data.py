import mysql.connector
import random
import datetime
import math

conn = mysql.connector.connect(
    host='localhost',
    user='root',
    password='aa111111',
    database='xfunds'
)
cursor = conn.cursor()

cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
cursor.execute("TRUNCATE TABLE fx_option_trade")
cursor.execute("TRUNCATE TABLE fx_swap_trade")
cursor.execute("TRUNCATE TABLE fx_forward_trade")
cursor.execute("TRUNCATE TABLE fx_spot_trade")
cursor.execute("TRUNCATE TABLE fx_trade_master")
cursor.execute("TRUNCATE TABLE fx_trade_lifecycle")
cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
conn.commit()

cursor.execute("SELECT customer_id, customer_name FROM fx_customer")
customers = cursor.fetchall()

purpose_codes = ['101', '102', '103', '104', '105', '201', '202', '203', '301', '302']
fx_purpose_codes = ['01', '02', '03', '04', '05', '06', '07', '08']

currency_config = [
    {'pair': 'USD/CNY', 'base': 'USD', 'quote': 'CNY', 'spot': 7.2269, 'fp_1d': 0.0012, 'fp_sw': 0.0035, 'fp_1m': 0.0156, 'fp_3m': 0.0425, 'fp_6m': 0.0895, 'fp_1y': 0.1750},
    {'pair': 'EUR/CNY', 'base': 'EUR', 'quote': 'CNY', 'spot': 7.5291, 'fp_1d': 0.0085, 'fp_sw': 0.0245, 'fp_1m': 0.1085, 'fp_3m': 0.2985, 'fp_6m': 0.6285, 'fp_1y': 1.2285},
    {'pair': 'JPY/CNY', 'base': 'JPY', 'quote': 'CNY', 'spot': 0.0442, 'fp_1d': 0.00002, 'fp_sw': 0.00005, 'fp_1m': 0.00025, 'fp_3m': 0.00070, 'fp_6m': 0.00150, 'fp_1y': 0.00295},
    {'pair': 'GBP/CNY', 'base': 'GBP', 'quote': 'CNY', 'spot': 9.7537, 'fp_1d': 0.0125, 'fp_sw': 0.0375, 'fp_1m': 0.1650, 'fp_3m': 0.4625, 'fp_6m': 0.9750, 'fp_1y': 1.9250},
    {'pair': 'HKD/CNY', 'base': 'HKD', 'quote': 'CNY', 'spot': 0.9073, 'fp_1d': 0.0001, 'fp_sw': 0.0003, 'fp_1m': 0.0012, 'fp_3m': 0.0035, 'fp_6m': 0.0075, 'fp_1y': 0.0145},
    {'pair': 'AUD/CNY', 'base': 'AUD', 'quote': 'CNY', 'spot': 4.7728, 'fp_1d': 0.0035, 'fp_sw': 0.0105, 'fp_1m': 0.0455, 'fp_3m': 0.1265, 'fp_6m': 0.2685, 'fp_1y': 0.5250},
    {'pair': 'CAD/CNY', 'base': 'CAD', 'quote': 'CNY', 'spot': 5.3862, 'fp_1d': 0.0045, 'fp_sw': 0.0135, 'fp_1m': 0.0585, 'fp_3m': 0.1625, 'fp_6m': 0.3465, 'fp_1y': 0.6750},
    {'pair': 'SGD/CNY', 'base': 'SGD', 'quote': 'CNY', 'spot': 5.2299, 'fp_1d': 0.0038, 'fp_sw': 0.0115, 'fp_1m': 0.0505, 'fp_3m': 0.1405, 'fp_6m': 0.2985, 'fp_1y': 0.5800},
    {'pair': 'CHF/CNY', 'base': 'CHF', 'quote': 'CNY', 'spot': 7.7336, 'fp_1d': 0.0095, 'fp_sw': 0.0285, 'fp_1m': 0.1245, 'fp_3m': 0.3465, 'fp_6m': 0.7350, 'fp_1y': 1.4400},
    {'pair': 'NTD/CNY', 'base': 'NTD', 'quote': 'CNY', 'spot': 0.2150, 'fp_1d': 0.00005, 'fp_sw': 0.00015, 'fp_1m': 0.00065, 'fp_3m': 0.00185, 'fp_6m': 0.00395, 'fp_1y': 0.00770},
]

term_config = [('1D', 1), ('SW', 2), ('1M', 30), ('2M', 60), ('3M', 90), ('6M', 180), ('1Y', 365)]

def get_next_workday(date, days=2):
    result = date
    added = 0
    while added < days:
        result += datetime.timedelta(days=1)
        if result.weekday() < 5:
            added += 1
    return result

def random_time(date):
    hour = random.randint(9, 17)
    minute = random.randint(0, 59)
    second = random.randint(0, 59)
    return datetime.datetime(date.year, date.month, date.day, hour, minute, second)

biz_seq = {}

def generate_trade_id(prefix, make_time):
    time_part = make_time.strftime('%Y%m%d%H%M%S')
    random_part = random.randint(100, 999)
    return f"{prefix}{time_part}{random_part}"

def generate_business_no(prefix, make_time):
    date_part = make_time.strftime('%Y%m%d')
    key = f"{prefix}_{date_part}"
    if key not in biz_seq:
        biz_seq[key] = 0
    biz_seq[key] += 1
    return f"{prefix}{date_part}{biz_seq[key]:04d}"

for i in range(3000):
    rand = random.random()
    if rand < 0.4:
        trade_type = 'SPOT'
    elif rand < 0.7:
        trade_type = 'FORWARD'
    elif rand < 0.9:
        trade_type = 'SWAP'
    else:
        trade_type = 'OPTION'
    
    rand = random.random()
    if rand < 0.6:
        status = 'SETTLED'
    elif rand < 0.85:
        status = 'ACTIVE'
    elif rand < 0.92:
        status = 'PENDING_CHECK'
    elif rand < 0.96:
        status = 'DRAFT'
    else:
        status = 'REJECTED'
    
    customer = customers[i % len(customers)]
    customer_id, customer_name = customer[0], customer[1]
    
    rand = random.random()
    if rand < 0.5:
        branch_code = '100000'
        maker_id = 1 if random.random() < 0.5 else 5
        checker_id = 6 if status in ('SETTLED', 'ACTIVE') else None
    elif rand < 0.8:
        branch_code = '110000'
        maker_id = 2
        checker_id = 3 if status in ('SETTLED', 'ACTIVE') else None
    else:
        branch_code = '110100'
        maker_id = 1 if random.random() < 0.5 else 5
        checker_id = 6 if status in ('SETTLED', 'ACTIVE') else None
    
    currency = random.choice(currency_config)
    
    start_date = datetime.date(2025, 1, 1)
    end_date = datetime.date.today()
    delta_days = (end_date - start_date).days
    trade_date = start_date + datetime.timedelta(days=random.randint(0, delta_days))
    
    make_time = random_time(trade_date)
    
    check_time = None
    authorize_time = None
    if status == 'SETTLED':
        check_date = trade_date + datetime.timedelta(days=random.randint(0, 1))
        authorize_date = get_next_workday(check_date, 2)
        check_time = random_time(check_date)
        authorize_time = random_time(authorize_date)
    elif status == 'ACTIVE':
        check_date = trade_date + datetime.timedelta(days=random.randint(0, 1))
        check_time = random_time(check_date)
    
    notional_amount = round(100000 + random.random() * 99900000, 2)
    
    spot_rate = currency['spot']
    customer_rate = round(spot_rate * (1 + random.random() * 0.005 - 0.0025), 4)
    cost_rate = round(spot_rate * (1 + random.random() * 0.003 - 0.0015), 4)
    
    counter_amount = round(notional_amount * spot_rate, 2)
    
    margin_amount = round(notional_amount * 0.10, 2)
    
    prefix = {'SPOT': 'SP', 'FORWARD': 'FW', 'SWAP': 'SW', 'OPTION': 'OP'}[trade_type]
    trade_id = generate_trade_id(prefix, make_time)
    business_no = generate_business_no(prefix, make_time)
    
    delivery_type = 'DELIVERY' if random.random() < 0.9 else 'NETTING'
    
    purpose_code = random.choice(purpose_codes)
    fx_purpose_code = random.choice(fx_purpose_codes)
    
    if trade_type == 'SPOT':
        trade_direction = 'BUY' if random.random() < 0.5 else 'SELL'
        value_date = get_next_workday(trade_date)
        maturity_date = trade_date
        
        cursor.execute('''
            INSERT INTO fx_trade_master 
            (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, base_currency, quote_currency, currency_pair,
             notional_amount, counter_amount, trade_direction, value_date, trade_date,
             maturity_date, delivery_type, settlement_method, spot_rate, customer_rate,
             cost_rate, branch_profit_point, special_trade_type, maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, currency['base'], currency['quote'], currency['pair'],
             notional_amount, counter_amount, trade_direction, value_date, trade_date,
             maturity_date, delivery_type, 'FULL', spot_rate, customer_rate,
             cost_rate, round((customer_rate - cost_rate) * 10000, 2), 'NORMAL', maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code))
        
        cursor.execute('''
            INSERT INTO fx_spot_trade 
            (trade_id, settlement_type, spot_rate, customer_rate, cost_rate, 
             currency1_account, currency2_account, margin_account_id, margin_amount, amount)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, delivery_type, spot_rate, customer_rate, cost_rate,
             f"ACCT_{currency['base']}_{i%100}", f"ACCT_{currency['quote']}_{i%100}",
             f"MARGIN_{i%100}", margin_amount, notional_amount))
    
    elif trade_type == 'FORWARD':
        trade_direction = 'BUY' if random.random() < 0.5 else 'SELL'
        term, term_days = random.choice(term_config)
        value_date = trade_date
        maturity_date = get_next_workday(trade_date) + datetime.timedelta(days=term_days)
        
        fp_key = {'1D': 'fp_1d', 'SW': 'fp_sw', '1M': 'fp_1m', 
                  '2M': 'fp_3m', '3M': 'fp_3m', '6M': 'fp_6m', '1Y': 'fp_1y'}[term]
        forward_point = currency[fp_key]
        forward_rate = round(spot_rate + forward_point, 4)
        
        cursor.execute('''
            INSERT INTO fx_trade_master 
            (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, base_currency, quote_currency, currency_pair,
             notional_amount, counter_amount, trade_direction, value_date, trade_date,
             maturity_date, delivery_type, settlement_method, spot_rate, customer_rate,
             cost_rate, branch_profit_point, special_trade_type, maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, currency['base'], currency['quote'], currency['pair'],
             notional_amount, counter_amount, trade_direction, value_date, trade_date,
             maturity_date, delivery_type, 'FULL', spot_rate, forward_rate,
             cost_rate, round((forward_rate - cost_rate) * 10000, 2), 'NORMAL', maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code))
        
        cursor.execute('''
            INSERT INTO fx_forward_trade 
            (trade_id, maturity_date, forward_rate, forward_point, term, 
             settlement_method, currency1_account, currency2_account,
             margin_account_id, margin_amount, amount, 
             is_rolled_over, early_delivery_flag, early_default_flag)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, maturity_date, forward_rate, forward_point, term, 'FULL',
             f"ACCT_{currency['base']}_{i%100}", f"ACCT_{currency['quote']}_{i%100}",
             f"MARGIN_{i%100}", margin_amount, notional_amount, 'N', 'N', 'N'))
    
    elif trade_type == 'SWAP':
        near_direction = 'BUY' if random.random() < 0.5 else 'SELL'
        far_direction = 'SELL' if near_direction == 'BUY' else 'BUY'
        term, term_days = random.choice(term_config[1:])
        
        near_value_date = get_next_workday(trade_date)
        far_value_date = get_next_workday(trade_date) + datetime.timedelta(days=term_days)
        value_date = near_value_date
        maturity_date = far_value_date
        
        fp_key = {'1D': 'fp_1d', 'SW': 'fp_sw', '1M': 'fp_1m', 
                  '2M': 'fp_3m', '3M': 'fp_3m', '6M': 'fp_6m', '1Y': 'fp_1y'}[term]
        swap_point = currency[fp_key]
        near_rate = round(spot_rate * (1 + random.random() * 0.003 - 0.0015), 4)
        far_rate = round(near_rate + swap_point, 4)
        
        near_customer_rate = round(near_rate * (1 + random.random() * 0.002 - 0.001), 4)
        far_customer_rate = round(far_rate * (1 + random.random() * 0.002 - 0.001), 4)
        near_cost_rate = round(near_rate * (1 + random.random() * 0.0015 - 0.00075), 4)
        far_cost_rate = round(far_rate * (1 + random.random() * 0.0015 - 0.00075), 4)
        
        cursor.execute('''
            INSERT INTO fx_trade_master 
            (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, base_currency, quote_currency, currency_pair,
             notional_amount, counter_amount, trade_direction, value_date, trade_date,
             maturity_date, delivery_type, settlement_method, spot_rate, customer_rate,
             cost_rate, branch_profit_point, special_trade_type, maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, currency['base'], currency['quote'], currency['pair'],
             notional_amount, counter_amount, near_direction, value_date, trade_date,
             maturity_date, delivery_type, 'FULL', spot_rate, near_customer_rate,
             near_cost_rate, round((near_customer_rate - near_cost_rate) * 10000, 2), 'NORMAL', maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code))
        
        cursor.execute('''
            INSERT INTO fx_swap_trade 
            (trade_id, swap_type, near_leg_direction, near_leg_amount, near_leg_rate, 
             near_leg_cost_rate, near_leg_customer_rate, near_leg_branch_profit_point, 
             near_leg_value_date, near_leg_currency1_account, near_leg_currency2_account, 
             near_leg_settlement_method, far_leg_direction, far_leg_amount, far_leg_rate, 
             far_leg_cost_rate, far_leg_customer_rate, far_leg_branch_profit_point, 
             far_leg_value_date, far_leg_currency1_account, far_leg_currency2_account, 
             far_leg_settlement_method, term, swap_point, near_spot_rate, is_pure_swap, 
             margin_account_id, margin_amount)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, 'REGULAR', near_direction, notional_amount, near_rate, near_cost_rate, near_customer_rate,
             round((near_customer_rate - near_cost_rate) * 10000, 2), near_value_date,
             f"ACCT_{currency['base']}_{i%100}", f"ACCT_{currency['quote']}_{i%100}", 'FULL',
             far_direction, notional_amount, far_rate, far_cost_rate, far_customer_rate,
             round((far_customer_rate - far_cost_rate) * 10000, 2), far_value_date,
             f"ACCT_{currency['base']}_{i%100}", f"ACCT_{currency['quote']}_{i%100}", 'FULL',
             term, swap_point, spot_rate, 'N', f"MARGIN_{i%100}", margin_amount))
    
    elif trade_type == 'OPTION':
        option_type = 'CALL' if random.random() < 0.5 else 'PUT'
        option_style = 'EUROPEAN' if random.random() < 0.7 else 'AMERICAN'
        buyer_seller = 'BUYER' if random.random() < 0.5 else 'SELLER'
        
        option_days = random.randint(30, 360)
        maturity_date = get_next_workday(trade_date) + datetime.timedelta(days=option_days)
        value_date = trade_date
        
        strike_price = round(spot_rate * (1 + random.random() * 0.2 - 0.1), 4)
        premium_amount = round(notional_amount * (0.01 + random.random() * 0.04), 2)
        
        observation_start = trade_date + datetime.timedelta(days=random.randint(0, 5))
        observation_end = maturity_date - datetime.timedelta(days=random.randint(0, 5))
        
        cursor.execute('''
            INSERT INTO fx_trade_master 
            (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, base_currency, quote_currency, currency_pair,
             notional_amount, counter_amount, trade_direction, value_date, trade_date,
             maturity_date, delivery_type, settlement_method, spot_rate, customer_rate,
             cost_rate, branch_profit_point, special_trade_type, maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, business_no, trade_type, status, branch_code,
             customer_id, customer_name, currency['base'], currency['quote'], currency['pair'],
             notional_amount, counter_amount, buyer_seller, value_date, trade_date,
             maturity_date, delivery_type, 'CASH', spot_rate, strike_price,
             cost_rate, 0, 'NORMAL', maker_id, checker_id,
             make_time, check_time, authorize_time, purpose_code, fx_purpose_code))
        
        exercise_time = random_time(maturity_date)
        cursor.execute('''
            INSERT INTO fx_option_trade 
            (trade_id, option_type, option_style, strike_price, premium_amount, 
             premium_currency, premium_value_date, premium_paid_flag, premium_account_id, 
             maturity_date, exercise_flag, abandon_flag, settlement_method, buyer_seller, 
             currency1_account, currency2_account, notional_amount, 
             observation_start_date, observation_end_date, exercise_time_point, 
             days, closed_amount, remaining_amount, reference_rate)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s,
                    %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ''', (trade_id, option_type, option_style, strike_price, premium_amount,
             currency['quote'], get_next_workday(trade_date), '0',
             f"ACCT_{currency['quote']}_{i%100}", maturity_date, '0', '0', 'CASH',
             buyer_seller, f"ACCT_{currency['base']}_{i%100}", f"ACCT_{currency['quote']}_{i%100}",
             notional_amount, observation_start, observation_end, exercise_time, option_days,
             0, notional_amount, spot_rate))
    
    if (i + 1) % 500 == 0:
        conn.commit()
        print(f"已生成 {i+1} 条数据")

conn.commit()

cursor.execute("SELECT trade_type, COUNT(*) FROM fx_trade_master GROUP BY trade_type")
print("\n交易类型分布：")
for row in cursor.fetchall():
    print(f"  {row[0]}: {row[1]} 条")

cursor.execute("SELECT COUNT(*) FROM fx_spot_trade")
print(f"\n即期子表: {cursor.fetchone()[0]} 条")
cursor.execute("SELECT COUNT(*) FROM fx_forward_trade")
print(f"远期子表: {cursor.fetchone()[0]} 条")
cursor.execute("SELECT COUNT(*) FROM fx_swap_trade")
print(f"掉期子表: {cursor.fetchone()[0]} 条")
cursor.execute("SELECT COUNT(*) FROM fx_option_trade")
print(f"期权子表: {cursor.fetchone()[0]} 条")

cursor.execute("SELECT COUNT(*) FROM fx_trade_master WHERE counter_amount IS NULL OR counter_amount = 0")
print(f"\n主表counter_amount为空或0的记录: {cursor.fetchone()[0]} 条")

cursor.execute("SELECT COUNT(*) FROM fx_trade_master WHERE spot_rate IS NULL OR customer_rate IS NULL")
print(f"主表汇率字段为空的记录: {cursor.fetchone()[0]} 条")

cursor.execute("SELECT COUNT(*) FROM fx_trade_master WHERE purpose_code IS NULL OR fx_purpose_code IS NULL")
print(f"主表用途编码字段为空的记录: {cursor.fetchone()[0]} 条")

cursor.execute("SELECT COUNT(*) FROM fx_forward_trade WHERE term IS NULL")
print(f"远期子表term为空的记录: {cursor.fetchone()[0]} 条")

cursor.execute("SELECT COUNT(*) FROM fx_trade_master WHERE status='SETTLED' AND (check_time IS NULL OR authorize_time IS NULL)")
print(f"SETTLED状态缺少check_time/authorize_time的记录: {cursor.fetchone()[0]} 条")

cursor.execute("SELECT COUNT(*) FROM fx_trade_master WHERE status='ACTIVE' AND check_time IS NULL")
print(f"ACTIVE状态缺少check_time的记录: {cursor.fetchone()[0]} 条")

cursor.execute("SELECT trade_id, trade_type, notional_amount, spot_rate, counter_amount, ROUND(notional_amount * spot_rate, 2) AS expected FROM fx_trade_master LIMIT 3")
print("\ncounter_amount验证（应为notional_amount * spot_rate）：")
for row in cursor.fetchall():
    print(f"  {row[0]}: notional={row[2]}, spot_rate={row[3]}, counter={row[4]}, expected={row[5]}")

cursor.execute("SELECT trade_id, business_no FROM fx_trade_master LIMIT 3")
print("\n交易ID和业务编号格式验证：")
for row in cursor.fetchall():
    print(f"  trade_id={row[0]}, business_no={row[1]}")

cursor.close()
conn.close()
print("\n数据生成完成！")