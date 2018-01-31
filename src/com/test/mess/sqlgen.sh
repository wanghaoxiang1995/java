#!/bin/bash
month=2018-01-


for day in $(seq 15 24)
do
	stime=${month}${day}
	etime=${month}$((${day}+1))
	#echo "day:${day}   stime:${stime}   etime:${etime}"
	echo "SELECT '$stime' 日期,a.account_id 帐号,b.price * 0.01 充值, c.cash_money * 0.01 提现 , d.price * 0.01 购买, e.price * 0.01 退款 , f.balance 余额 FROM (
                                      SELECT account_id FROM pay_recharge WHERE create_time >= '$stime' and create_time <='$etime' 
                                      UNION 
                                      SELECT account_id FROM pay_cash WHERE cash_status = 'cashed' and pay_cash_time >= '$stime' and pay_cash_time <= '$etime'
                                      UNION
                                      SELECT from_account_id account_id FROM pay_trade WHERE create_time >= '$stime' and create_time <='$etime' and debit_type = 2 and type = 2 
                                      UNION
                                      SELECT to_account_id account_id FROM pay_trade WHERE create_time >= '$stime' and create_time <='$etime' and debit_type = 2 and type = 3
                                      ) a 
                                      LEFT JOIN(SELECT account_id,sum(price) price FROM pay_recharge b  WHERE create_time >= '$stime' and create_time <='$etime' GROUP BY account_id   ) b on a.account_id = b.account_id
                                      LEFT JOIN (SELECT account_id,sum(cash_money) cash_money FROM pay_cash c  WHERE c.cash_status = 'cashed' and c.pay_cash_time >= '$stime' and c.pay_cash_time <= '$etime' GROUP BY account_id) c on a.account_id = c.account_id 
                                      LEFT JOIN (SELECT from_account_id account_id, sum(price) price FROM pay_trade d  WHERE d.create_time >= '$stime' and d.create_time <='$etime' and debit_type = 2 and d.type = 2 GROUP BY from_account_id ) d on a.account_id = d.account_id
                                      LEFT JOIN (SELECT to_account_id account_id, sum(price) price FROM pay_trade e WHERE e.create_time >= '$stime' and e.create_time <='$etime' and debit_type = 2 and e.type = 3 GROUP BY to_account_id) e on a.account_id = e.account_id 
                                      LEFT JOIN (SELECT account_id ,substring_index(group_concat(f.to_money * 0.01 order by f.create_time desc),',',1) as balance  FROM pay_xzb_record f WHERE   f.create_time >= '$stime' and f.create_time <= '$etime'  GROUP BY account_id) f on a.account_id = f.account_id 
                                      ORDER BY f.balance desc;"
done
