package com.test.mess;

/**
 * 说明：
 * 项目：java
 * 路径：com.test.mess.SqlGenerate
 * 创建人：wanghaoxiang
 * 时间：18-1-4 下午5:22
 */
public class SqlGenerate {
    
    static String fromTime = "'2018-01-15'";
    static String toTime = "'2018-01-16'";
    
    
    public static void main(String[] args) {
        
        System.out.println("\n\n"+generateAggrSql()+"\n\n\n\n");
        
    }
    
    /**
     * 生成sql
     * @return
     */
    public static String generateAggrSql(){
        return String.format("SELECT  %s 日期,a.account_id 帐号,b.price * 0.01 充值, c.cash_money * 0.01 提现 , d.price * 0.01 购买, e.price * 0.01 退款 , f.balance 余额 FROM (\n"
                                     + "SELECT account_id FROM pay_recharge WHERE create_time >= %s and create_time <=%s \n"
                                     + "UNION \n"
                                     + "SELECT account_id FROM pay_cash WHERE cash_status = 'cashed' and pay_cash_time >= %s and pay_cash_time <= %s\n"
                                     + "UNION\n"
                                     + "SELECT from_account_id account_id FROM pay_trade WHERE create_time >= %s and create_time <=%s and debit_type = 2 and type = 2 \n"
                                     + "UNION\n"
                                     + "SELECT to_account_id account_id FROM pay_trade WHERE create_time >= %s and create_time <=%s and debit_type = 2 and type = 3\n"
                                     + ") a \n"
                                     + "LEFT JOIN(SELECT account_id,sum(price) price FROM pay_recharge b  WHERE create_time >= %s and create_time <=%s GROUP BY account_id   ) b on a.account_id = b.account_id\n"
                                     + "LEFT JOIN (SELECT account_id,sum(cash_money) cash_money FROM pay_cash c  WHERE c.cash_status = 'cashed' and c.pay_cash_time >= %s and c.pay_cash_time <= %s GROUP BY account_id) c on a.account_id = c.account_id \n"
                                     + "LEFT JOIN (SELECT from_account_id account_id, sum(price) price FROM pay_trade d  WHERE d.create_time >= %s and d.create_time <=%s and debit_type = 2 and d.type = 2 GROUP BY from_account_id ) d on a.account_id = d.account_id\n"
                                     + "LEFT JOIN (SELECT to_account_id account_id, sum(price) price FROM pay_trade e WHERE e.create_time >= %s and e.create_time <=%s and debit_type = 2 and e.type = 3 GROUP BY to_account_id) e on a.account_id = e.account_id \n"
                                     + "LEFT JOIN (SELECT account_id ,substring_index(group_concat(f.to_money * 0.01 order by f.create_time desc),\",\",1) as balance  FROM pay_xzb_record f WHERE   f.create_time >= %s and f.create_time <= %s  GROUP BY account_id) f on a.account_id = f.account_id \n"
                                     + "ORDER BY f.balance desc;"
                ,fromTime,
                fromTime,toTime,
                fromTime,toTime,
                fromTime,toTime,
                fromTime,toTime,
                fromTime,toTime,
                fromTime,toTime,
                fromTime,toTime,
                fromTime,toTime,
                fromTime,toTime
        );
    }
    
    public static String generateBalance(){
        return String.format("SELECT a.account_id, substring_index(group_concat(f.to_money * 0.01 order by f.create_time desc),\",\",1) as 余额 FROM (\n"
                                     + "SELECT account_id FROM pay_recharge WHERE create_time >= %s and create_time <=%s \n"
                                     + "UNION \n"
                                     + "SELECT account_id FROM pay_cash WHERE cash_status = 'cashed' and pay_cash_time >= %s and pay_cash_time <= %s\n"
                                     + "UNION\n"
                                     + "SELECT from_account_id account_id FROM pay_trade WHERE create_time >= %s and create_time <=%s and debit_type = 2 and type = 2 \n"
                                     + "UNION\n"
                                     + "SELECT to_account_id account_id FROM pay_trade WHERE create_time >= %s and create_time <=%s and debit_type = 2 and type = 3\n"
                                     + ") a , pay_xzb_record f WHERE a.account_id = f.account_id and f.create_time >= %s and f.create_time <= %s \n"
                                     + "GROUP BY a.account_id ORDER BY a.account_id asc;"
                ,fromTime,toTime
                ,fromTime,toTime
                ,fromTime,toTime
                ,fromTime,toTime
                ,fromTime,toTime
        );
    }
    
}
