package com.peanut.base.pay.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.peanut.base.pay.dao.PnPayRecordDao;
import com.peanut.base.pay.entity.PnPayRecord;
import com.peanut.base.pay.service.PnPayService;
import com.peanut.base.user.dao.PnUserCoinsDao;
import com.peanut.base.user.dao.PnUserDao;
import com.peanut.base.user.entity.PnUserCoins;
import com.peanut.base.user.entity.PnUserLogin;
import com.peanut.commons.base.ReturnModel;
import com.peanut.commons.constants.CodeConstants;
import com.peanut.commons.constants.InitContants;
import com.peanut.commons.constants.RedisContant;
import com.peanut.commons.constants.UserConstants;
import com.peanut.commons.pay.iapppay.IAppPaySDKConfig;
import com.peanut.commons.pay.iapppay.IAppPayUtil;
import com.peanut.commons.redis.RedisDao;

@Service
public class PnPayServiceImpl implements PnPayService {
	
	private static Logger paylogger = Logger.getLogger("paylog");
	@Autowired
	private PnUserCoinsDao coinsDao;
	@Autowired
	private PnPayRecordDao payDao;
	@Autowired
	private RedisDao redisDao;
	@Autowired
	private PnUserDao userDao;
	@Override
	public ReturnModel getUserCoins(Long uid) {
		ReturnModel returnModel = new ReturnModel();
		try {
			if (uid != null ) {
				PnUserCoins coins = coinsDao.selectByPrimaryKey(uid);
				Map<String, Object> coinsMap = new HashMap<String, Object>();
				if(coins!=null){
					coinsMap.put("usercoins", coins.getUserCoins());
				}else{
					coinsMap.put("usercoins", 0);
				}
				returnModel.setData(coinsMap);
			} else {
				returnModel.setCode(CodeConstants.CONLOGIN);
				returnModel.setMessage("用户ID为空");
			}

		} catch (Exception e) {
			paylogger.info("余额查询：错误信息---uid:"+uid,e);
			returnModel.setCode(CodeConstants.ERROR);
			returnModel.setMessage("系统报错");
		}
		return returnModel;
	}
	
	@Override
	public String iAppPayCallback(String transdata) throws Exception {
		Date ctime = new Date();
		paylogger.info("进入接口：iAppPayCallback，参数 transdata:"+transdata);
		if(transdata != null && !transdata.isEmpty()){
			com.alibaba.fastjson.JSONObject json = JSON.parseObject(transdata);
			String cporderid = json.getString("cporderid");
			Float money = json.getFloat("money");
			Integer result = json.getInteger("result");
			PnPayRecord payrecord = this.payDao.selectByOrderId(cporderid);
			if(payrecord!=null){//订单号在数据库中有数据，并且返回状态为“成功”
				paylogger.info("【cporderid】"+cporderid+"[result]"+result);
				payrecord.setCallbackTime(ctime);
				payrecord.setAmount(Integer.valueOf((int) (money*100)));
				payrecord.setErroCode(result.toString());
				if(result==0){
					payrecord.setPaystatus(UserConstants.PAY_PAYSTATUES_SUCCESS);//状态为充值成功
					//乐币表中更新数据
					Map<String, Object> paramMap = new HashMap<String, Object>();
					paramMap.put("uid", payrecord.getUid());
					paramMap.put("num", payrecord.getAmount());
					try {
						PnUserCoins usercoins = this.coinsDao.selectByUidForUpdate(payrecord.getUid());
						paylogger.info("【usercoins】"+usercoins);
						Long tal = 0l;
						if(usercoins!=null){
							tal = usercoins.getUserCoins() + payrecord.getCoin();
						}else{
							PnUserCoins coins = new PnUserCoins();
							coins.setUid(payrecord.getUid());
							coinsDao.insertSelective(coins);
						}
						//paylogger.info("【爱贝支付】"+"实际增加数量"+paramMap.toString()+",充值前余额："+usercoins.getUserCoins()+",充值后余额："+tal);
						//setCoinsInRedis(payrecord.getUid(), tal);
						this.coinsDao.updateAddcoins(paramMap);
						payrecord.setStatus(UserConstants.PAY_STATUES_SUCCESS);
						payrecord.setRemainingAmount(tal);
					} catch (Exception e) {
						paylogger.info("支付回调",e);
						payrecord.setStatus(UserConstants.PAY_STATUES_FAIL);
						throw e;
					}
					payrecord.setPayTime(new Date());
				}else{
					payrecord.setStatus(UserConstants.PAY_PAYSTATUES_FAIL);//充值失败
				}
				this.payDao.updateByPrimaryKeySelective(payrecord);
			}
			return "SUCCESS";
		}else{
			return "FAILURE";
		}
	}
	@Override
	public ReturnModel getUserPayRecording(Long uid, Integer start, Integer rows) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public ReturnModel iapppaybuy(Long uid, Integer coins, Integer amount,
			Integer platform, String payChannelType) {
		ReturnModel returnModel = new ReturnModel();
		Date ctime = new Date();
		try {
			paylogger.info("进入接口：ipaynowbuy，参数 uid:"+uid+",coins:"+coins+",amount:"+amount);
			if(amount==null){
				returnModel.setCode(CodeConstants.ERROR);
				returnModel.setMessage("amount为空");
				return returnModel;
			}
			String callbackurl = InitContants.SERVICE_IP+IAppPaySDKConfig.NOTIFYURL;
			SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");//精确到毫秒
			String orderid = "PNIPAY_"+formatter.format(ctime)+"_"+uid+"_"+coins;//商户生成的唯一订单号，最长40位

			String terminalid = "00-00-00-00";//终端标识
			String userip = "127.0.0.1";
			PnUserLogin userlogin = redisDao.hget(
					RedisContant.PN_USER_LOGIN_INFO, uid.toString(),
					PnUserLogin.class);
			if(userlogin!=null){
				terminalid = userlogin.getMac();
				userip = userlogin.getIp();
				paylogger.info("参数 userip:"+userip+",terminalid:"+terminalid);
			}
			String waresname = "人民币-"+coins+"分";//商品描述
			float price = (float)amount/100;
			String reqData = IAppPayUtil.ReqData(waresname, orderid, price, uid.toString(), waresname, callbackurl);
			//返回前端的数据
			Map<String,Object> remap = new HashMap<>();
			remap.put("reqData", reqData);
			remap.put("orderid", orderid);
			returnModel.setData(remap);
			
			
			//数据存入数据库
			PnPayRecord payrecord = new PnPayRecord();
			payrecord.setMyOrderid(orderid);
			payrecord.setAmount(amount);
			payrecord.setUid(uid);
			payrecord.setCtime(ctime);
			payrecord.setPayType(UserConstants.PAY_AIPAY);
			payrecord.setCoin(coins);
			payrecord.setStatus(UserConstants.PAY_STATUES_WEI);
			payrecord.setPaystatus(UserConstants.PAY_PAYSTATUES_WEI);
			payrecord.setPlatform(platform);
			//payrecord.setPayChannelType(Integer.valueOf(payChannelType));
			payDao.insertSelective(payrecord);
		} catch (Exception e) {
			paylogger.info("易宝支付：错误信息："+e.getMessage()+"参数：uid"+uid+"---coins:"+coins+"---amount:"+amount+"---platform:"+platform+"---payChannelType:"+payChannelType);
			returnModel.setCode(CodeConstants.ERROR);
			returnModel.setMessage("系统报错");
			e.printStackTrace();
		}
		return returnModel;
	}
	@Override
	public ReturnModel cancelpay(Long uid, String orderid) {
		ReturnModel returnModel = new ReturnModel();
		if(uid!=null && orderid!=null){
			PnPayRecord payrecord = payDao.selectByOrderId(orderid);
			if(payrecord!=null&& payrecord.getUid()!=null){
				if(uid.equals(payrecord.getUid())){//说明是同一用户
					PnPayRecord newpay = new PnPayRecord();
					newpay.setStatus(UserConstants.PAY_STATUES_CANCEL);
					newpay.setId(payrecord.getId());
					payDao.updateByPrimaryKeySelective(newpay);
				}else{
					returnModel.setCode(CodeConstants.PayOrderExits);
					returnModel.setMessage("订单ID与用户ID不对应");
				}
			}else{
				returnModel.setCode(CodeConstants.PayOrderExits);
				returnModel.setMessage("订单不存在");
			}
		}else{
			returnModel.setCode(CodeConstants.CONLOGIN);
			returnModel.setMessage("用户ID为空");
		}
		return returnModel;
	}
	@Override
	public ReturnModel iosbuy(Long uid, Integer coins, Integer amount,
			String product_id, String purchase_date_ms) {
		ReturnModel returnModel = new ReturnModel();
		Date ctime = new Date();
		try {
			paylogger.info("进入接口：iosbuy，参数 uid:"+uid+",coins:"+coins+",amount:"+amount+",purchase_date_ms:"+purchase_date_ms+",product_id:"+product_id);
			SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd_HHmmssSSS");//精确到毫秒
			String orderid = "PNIOS_"+formatter.format(ctime)+"_"+uid+"_"+coins;//商户生成的唯一订单号，最长40位
			//交易日期  
            Date backtime = ctime;
            if(purchase_date_ms!=null){
            	backtime = new Date(Long.valueOf(purchase_date_ms));
            }
            //数据存入数据库
			PnPayRecord payrecord = new PnPayRecord();
			payrecord.setMyOrderid(orderid);
			payrecord.setAmount(amount);
			payrecord.setPayTime(ctime);
			payrecord.setUid(uid);
			payrecord.setCtime(ctime);
			payrecord.setPayType(UserConstants.PAY_PINGGUO);
			payrecord.setCoin(coins);
			payrecord.setStatus(UserConstants.PAY_STATUES_WEI);
			payrecord.setPaystatus(UserConstants.PAY_PAYSTATUES_SUCCESS);
			payrecord.setPlatform(2);
			payrecord.setCallbackTime(backtime);
			payrecord.setPaidamount(Integer.valueOf(amount));
			payrecord.setOtherOrderid(product_id);
			payrecord.setErroCode("0");
			payrecord.setStatus(UserConstants.PAY_STATUES_SUCCESS);
			payrecord.setRemainingAmount(Long.valueOf(amount));
			//余额表中更新数据
			Map<String, Object> paramMap = new HashMap<String, Object>();
			paramMap.put("uid", payrecord.getUid());
			paramMap.put("num", payrecord.getCoin());
			PnUserCoins usercoins = coinsDao.selectByUidForUpdate(payrecord.getUid());
			Long tal = 0l;
			if(usercoins!=null){
				tal = usercoins.getUserCoins()+payrecord.getCoin();
			}else{
				PnUserCoins coins1 = new PnUserCoins();
				coins1.setUid(payrecord.getUid());
				coinsDao.insertSelective(coins1);
			}
			//paylogger.info("余额实际增加数量"+paramMap.toString()+",充值前余额："+usercoins.getUserCoins()+",充值后余额："+tal);
			coinsDao.updateAddcoins(paramMap);
			payrecord.setRemainingAmount(tal);
			payDao.insertSelective(payrecord);
			Map<String, Object> reMap = new HashMap<String, Object>();
			reMap.put("usercoins", tal);
			returnModel.setData(reMap);
		} catch (Exception e) {
			paylogger.info("苹果支付：错误信息---uid:"+uid,e);
			returnModel.setCode(CodeConstants.ERROR);
			returnModel.setMessage("系统报错");
		}
		return returnModel;
	}
	@Override
	public ReturnModel getPayDes(Integer type) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public ReturnModel upPayDes(String des, Integer type) {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public ReturnModel upUserCoins(Long id, Long coins) {
		// TODO Auto-generated method stub
		return null;
	}
	

	/**
	  * @Title: upexpintegral 
	  * @Description: 更新经验积分
	  * @param @param exp
	  * @param @param integral
	  * @param @param uid
	  * @return void
	  * @throws
	 */
	private void upexpintegral(Long exp,Long integral,Long uid){
		
	}
	
}
