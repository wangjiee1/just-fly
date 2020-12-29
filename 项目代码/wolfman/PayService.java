package wolfman.services;

import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import wolfman.async.AsyncManager;
import wolfman.core.cache.redis.RedisBucket;
import wolfman.core.cache.redis.RedisKeyPrefix;
import wolfman.dictionary.PayConfig;
import wolfman.dictionary.lib.PayConfigLib;
import wolfman.room.state.Dateutils;
import wolfman.web.dao.PayDao;
import wolfman.web.dao.UserConsumptionDao;
import wolfman.web.dao.domain.PayOrderVO;
import wolfman.web.db.domain.PayOrderModel;
import wolfman.web.db.domain.UserConsumptionModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by wsc on 2017/4/24.
 */
public class PayService extends BaseService{

    private final static Logger LOG = Logger
            .getLogger(PayService.class);

    private static class PayServiceHolder {
        private static wolfman.services.PayService cache = new wolfman.services.PayService();
    }
    public static PayService getInstance() {
        return PayService.PayServiceHolder.cache;
    }

    /**
     * 生成订单ID
     * @param clientId
     * @param amount
     * @param paidAmount
     * @param coins
     * @return
     */
    public String getOrderId(String clientId, String amount, String paidAmount, String coins, Integer os, String paytype) {
        try {
            if (os == 3){
                Integer intcouns = Integer.valueOf(coins);
                Integer intamount = Integer.valueOf(amount);
                if (intcouns * 10 > intamount){
                    return null;
                }
            }
            String orderid = "WOLF_"+ Dateutils.getorderdate()+"_"+clientId+"_"+coins;//商户生成的唯一订单号，最长40位
            PayDao.getInstance().addUserPay(clientId,orderid,Double.valueOf(amount),Double.valueOf(paidAmount),Integer.valueOf(coins),os, paytype,0,"");
            return orderid;
        }catch (Exception e){
            LOG.error("getOrderId>>", e);
        }
        return null;
    }

    /**
     * 充值回调
     * @param joyorderid
     * @param orderid
     */
    public void callbackPay(String joyorderid,String orderid,Integer amountjoy){
        //System.out.println(">>>>>>>>>>>>callbackPay >>>>>>>>>>>>>"+joyorderid+">>>>orderid:"+orderid+">>>>>>amount:"+amount);
        if (orderid!=null && !orderid.isEmpty() && joyorderid!=null && !joyorderid.isEmpty()){
            //orderid查询订单
            PayOrderModel orderModel = PayDao.getInstance().getUserOrder(orderid);
            if (orderModel != null && orderModel.getStatus() != 2){
                String details = "";
                //更新用户财产
                Integer amount = orderModel.getAmount();//订单中充值的钱
                int addcoins = orderModel.getCoins();//订单中的货币
                if (amount.intValue() != amountjoy.intValue()){//两边的充值金额不对，以第三方为准
                    details = "异常充值，原订单金额:"+amount+"货币："+addcoins;
                    amount = amountjoy;
                    addcoins = amount/10;

                }

                if (orderModel.getOs() == 2 && addcoins*10 == orderModel.getAmount()){//说明是老版本的ios充值，获得钻石*70%
                    addcoins = (int)(addcoins * 0.7);
                }

                UserService.getInstance().addUserCurrencyDiamondnum(orderModel.getDstuid(),addcoins);
                //更新订单信息
                PayDao.getInstance().updateUserPay(orderid,2,joyorderid,details);
                final int intuid = orderModel.getDstuid();
                final int changenum = orderModel.getCoins();

                //异步处理
                AsyncManager.getInstance().execute(new Runnable() {
                    @Override
                    public void run() {
                        //记录用户财产变化
                        UserConsumptionDao.getInstance().addUserConsumption(intuid,0,0,
                                changenum,
                                UserConsumptionModel.ChangeWay.Recharge,UserConsumptionModel.CurrencyType.DIAMOND);
                    }
                });

                //更新首充缓存  没有首充了
                /*Jedis jedis = null;
                try {
                    JedisPool jedisPool = RedisBucket.getInstance().getNoShardPool("user");
                    jedis = jedisPool.getResource();
                    String uidkey = String.format(RedisKeyPrefix.UserPayKey,orderModel.getDstuid());
                    jedis.hincrBy(uidkey,amount.toString(),1);
                    String times = jedis.hget(uidkey,amount.toString());
                    Integer tinum = Integer.valueOf(times);
                    if (tinum <= 1){
                        //首充，增加绑钻
                        UserService.getInstance().addUserCurrencyBindDiamondnum(orderModel.getDstuid(),PayConfigLib.getGiftDiamond(orderModel.getCoins()));
                        //异步处理
                        AsyncManager.getInstance().execute(new Runnable() {
                            @Override
                            public void run() {
                                //记录用户财产变化
                                UserConsumptionDao.getInstance().addUserConsumption(intuid,0,0,
                                        changenum,
                                        UserConsumptionModel.ChangeWay.Recharge,UserConsumptionModel.CurrencyType.BINDGOLD);
                            }
                        });
                    }
                }catch (Exception e){
                    LOG.error("callbackPay>>", e);
                }finally {
                    if (jedis!=null){
                        jedis.close();
                    }
                }*/
            }
        }

    }

    /**
     * 充值列表
     * @param clientId
     * @return
     */
    public List<PayConfig> getPayList(String clientId) {
        List<PayConfig> payConfigList = PayConfigLib.getPayList();
        Jedis jedis = null;
        try {
            JedisPool jedisPool = RedisBucket.getInstance().getNoShardPool("user");
            jedis = jedisPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserPayKey,clientId);
            Map<String, String> hashMap = jedis.hgetAll(uidkey);
            if (hashMap != null && hashMap.size() > 0){
                for (PayConfig pay : payConfigList){
                    String val = hashMap.get(String.valueOf(pay.getAmount() * 100));
                    if (val!=null && !val.isEmpty()) {
                        int valnum = Integer.valueOf(val);
                        if (valnum > 0 ){
                            pay.setIsfirst(1);
                        }
                    }
                }
            }
        }catch (Exception e){
            LOG.error("getPayList>>>",e);
        }finally {
            if (jedis!=null){
                jedis.close();
            }
        }
        return payConfigList;
    }

    public List<PayOrderVO> getPayOrderModelList(String clientId, String yearmonth){
        List<PayOrderVO> payOrderModelList = PayDao.getInstance().getUserOrderList(clientId,yearmonth);
        return payOrderModelList;
    }
}
