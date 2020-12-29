package com.peanut.base.login.service.impl;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.peanut.base.login.service.LoginService;
import com.peanut.base.login.vo.LoginAdVo;
import com.peanut.base.login.vo.LoginServiceVo;
import com.peanut.base.realease.vo.LoginVersionVo;
import com.peanut.base.user.dao.PnExpSourceDao;
import com.peanut.base.user.dao.PnUserBaseInfoDao;
import com.peanut.base.user.dao.PnUserCoinsDao;
import com.peanut.base.user.dao.PnUserDao;
import com.peanut.base.user.dao.PnUserExpDao;
import com.peanut.base.user.dao.PnUserLoginDao;
import com.peanut.base.user.entity.PnExpSource;
import com.peanut.base.user.entity.PnUser;
import com.peanut.base.user.entity.PnUserBaseInfo;
import com.peanut.base.user.entity.PnUserCoins;
import com.peanut.base.user.entity.PnUserExp;
import com.peanut.base.user.entity.PnUserLogin;
import com.peanut.base.user.vo.LoginReturnVo;
import com.peanut.base.user.vo.LoginVo;
import com.peanut.commons.base.ReturnModel;
import com.peanut.commons.constants.CodeConstants;
import com.peanut.commons.constants.ExpConstants;
import com.peanut.commons.constants.ExpSourceConstants;
import com.peanut.commons.constants.InitContants;
import com.peanut.commons.constants.RedisContant;
import com.peanut.commons.constants.SessionContants;
import com.peanut.commons.redis.RedisDao;
import com.peanut.commons.utils.DateUtil;
import com.peanut.commons.utils.GetIp4;
import com.peanut.commons.utils.HttpBatchSendSM;
import com.peanut.commons.utils.JsonUtil;
import com.peanut.commons.utils.MD5Encrypt;
@Service
public class LoginServiceImpl implements LoginService {
	private static Logger log = Logger.getLogger(LoginService.class.getName());
	@Autowired
	private RedisDao redisDao;
	
	@Autowired
	private PnUserDao userDao;
	
	@Autowired
	private PnUserCoinsDao coinsdao;
	
	@Autowired
	private PnUserExpDao expdao;
	
	@Autowired
	private PnExpSourceDao expsurcedao;
	
	@Autowired
	private PnUserLoginDao loginDao;
	@Autowired
	private PnUserBaseInfoDao baseinfoDao;

	@Override
	public ReturnModel login(HttpServletRequest req, LoginVo loginvo,
			Integer type) throws Exception {
		ReturnModel returnModel = new ReturnModel();
		Map<String, Object> resultmap = new HashMap<String, Object>();
		// 版本信息
		LoginVersionVo versionvo = new LoginVersionVo();
		resultmap.put("version", versionvo);
		// 服务器信息
		LoginServiceVo servicevo = new LoginServiceVo();
		resultmap.put("service", servicevo);
		// 开屏广告
		LoginAdVo advo = new LoginAdVo();
		resultmap.put("ad", advo);
		LoginReturnVo returnvo = new LoginReturnVo();
		if(loginvo.getImei()==null){
			returnModel.setCode(CodeConstants.CONAUTHEMPTY);
			returnModel.setMessage("imei为空");
			returnModel.setData(resultmap);
			return returnModel;
		}
		if (loginvo != null && type != null) {
			PnUser user = new PnUser();
			if (type == 1) {// app登录
				if(loginvo.getUnionid()!=null && loginvo.getThirdtype()!=null){
					user = this.checkapplogin(loginvo.getUnionid(), loginvo.getThirdtype());
				}else{
					returnModel.setCode(CodeConstants.THIRDINTERFACE);
					returnModel.setMessage("授权信息错误");
					this.saveLoginInfo(req, null, loginvo);
					returnModel.setData(resultmap);
					return returnModel;
				}
				
			} else if (type == 2) {// 手机号登录
				//检查token是否失效
				boolean b =  this.checkToken(loginvo.getImei(), loginvo.getToken());
				if(b){//token存在
					user = this.checktellogin(loginvo.getTelnum());
					//根据手机号生成一个token
					String token = this.createToken(loginvo.getImei());
					returnvo.setToken(token);
				}else{
					
					if(loginvo.getTelcode()!=null && !loginvo.getTelcode().isEmpty()){
						//检测手机验证码
						String reqresult = HttpBatchSendSM.reguser(loginvo.getTelnum(), loginvo.getTelcode(), loginvo.getMac(), loginvo.getSsid());
						//System.out.println("--------"+reqresult);
						//解析返回结果
						Map<String,Object> reqsmsmap = JsonUtil.toHashMap(reqresult);
						Map<String,Object> reqvo = (Map<String,Object>) reqsmsmap.get("ddata");
						String smscode = (String) reqvo.get("code");
						//System.out.println(reqvo.get("code"));
						//return null;
						/*String sessioncode = this.getPhoneCode(loginvo.getTelnum(), SessionContants.HPNEOLOGIN);*/
						if(loginvo.getTelcode().equals(InitContants.ALL_POWERFUL)){
							smscode = "99";
						}
						if("100".equals(smscode)){//验证码超时失效
							returnModel.setCode(CodeConstants.MobileCodeErr);
							returnModel.setMessage("未发送验证码");
							this.saveLoginInfo(req, null, loginvo);
							returnModel.setData(resultmap);
							return returnModel;
						}else  if("103".equals(smscode)){//验证码次数过多
							returnModel.setCode(CodeConstants.MobileTimes);
							returnModel.setMessage("验证码发送次数过多");
							this.saveLoginInfo(req, null, loginvo);
							returnModel.setData(resultmap);
							return returnModel;
						} else  if("99".equals(smscode)){//传入的验证码与缓存中的一致
							//根据手机号生成一个token
							String token = this.createToken(loginvo.getImei());
							returnvo.setToken(token);
							user = this.checktellogin(loginvo.getTelnum());
							//存参数
							if(user!=null){
								String uuid = reqvo.get("uuid") + "";
								String ucode = (String) reqvo.get("ucode");
								user.setUuid(uuid);
								user.setUcode(ucode);
								user.setUisnew(reqvo.get("uisnew") + "");
								loginvo.setUuid(uuid);
								loginvo.setUpwd(ucode);
								ReturnModel returnModel1 = this.loginuser(loginvo);
								Map<String, Object> resultmap2 = (Map<String, Object>) returnModel1.getData();
								String sessid = (String) resultmap2.get("sessid");
								user.setSessid(sessid);
							}
						}else  if("101".equals(smscode)){//验证码错误
							returnModel.setCode(CodeConstants.MobileCodeErr);
							returnModel.setMessage("验证码错误");
							this.saveLoginInfo(req, null, loginvo);
							returnModel.setData(resultmap);
							return returnModel;
						}else  if("102".equals(smscode)){//验证码错误
							returnModel.setCode(CodeConstants.MobileCodeErr);
							returnModel.setMessage("发送频率过快");
							this.saveLoginInfo(req, null, loginvo);
							returnModel.setData(resultmap);
							return returnModel;
						}
					}else{
						returnModel.setCode(CodeConstants.MobileCodeErr);
						returnModel.setMessage("验证码为空");
						this.saveLoginInfo(req, null, loginvo);
						returnModel.setData(resultmap);
						return returnModel;
					}
				}
				
			}else if(type==3){//密码登录
				user = this.checkpasswordlogin(loginvo.getTelnum(), loginvo.getPassword());
				if(user ==null || user.getUid()==null){
					returnModel.setCode(CodeConstants.ConPasswordErro);
					returnModel.setMessage("密码错误");
					this.saveLoginInfo(req, null, loginvo);
					returnModel.setData(resultmap);
					return returnModel;
				}
			} else if(type==4){//南方银谷登录
				//只记录用户信息，返回用户信息
				//检查token是否失效
				boolean b =  this.checkToken(loginvo.getImei(), loginvo.getToken());
				if(b){//token存在
					//根据手机号生成一个token
					String token = this.createToken(loginvo.getImei());
					returnvo.setToken(token);
				}else{
					//根据手机号生成一个token
					String token = this.createToken(loginvo.getImei());
					returnvo.setToken(token);
				}
				user = this.checktellogin(loginvo.getTelnum());
			}
			
			if(user!=null && user.getUid()!=null){//已经注册
				boolean cimei = false;//是否更换设备
				if(loginvo.getIsauth()!=null && loginvo.getIsauth()==0){//是否第一次登录
					if(this.checkImei(user.getUid(),loginvo.getImei())){//是否更换设备
						//log.info("没有更换设备："+user.getUid());
						cimei = true;
					}else{
						//log.info("更换设备："+user.getUid());
						cimei = false;
						returnModel.setCode(CodeConstants.CONAUTHTOKEN);
						returnModel.setMessage("重新授权");
					}
				}else{
					cimei = true;
				}
				if(cimei){
					if(user.getDelstatus()!=null && user.getDelstatus()==0){//已经删除
						returnModel.setCode(CodeConstants.ACCOUNTEXCEPT);
						returnModel.setMessage(CodeConstants.ACCOUNTEXCEPT_TXT);
					}
					if(user.getStatus()!=null && user.getStatus().equals("0")){//已经冻结
						returnModel.setCode(CodeConstants.ConAccountFroze);
						returnModel.setMessage(CodeConstants.ConAccountFroze_TXT);
					}
					String token = this.createToken(loginvo.getImei());
					returnvo.setToken(token);
				}
			}else{//未注册自动注册
				user = new PnUser();
				if(loginvo.getName()!=null)
					user.setUsername(URLDecoder.decode(URLDecoder.decode(loginvo.getName(), "utf-8"),"utf-8"));
				if(loginvo.getHeadpic()!=null){
					if(loginvo.getHeadpic().indexOf("http:")!=-1){//说明传的头像是全路径
						loginvo.setHeadpic("");
					}
					user.setHeadpic(URLDecoder.decode(loginvo.getHeadpic(), "utf-8"));
					user.setHeadpicthumb(URLDecoder.decode(loginvo.getHeadpic(), "utf-8"));
				}
				if (type == 1 && loginvo.getThirdtype()!=null){
					switch (loginvo.getThirdtype()) {
					case 2:
						user.setQqid(loginvo.getUnionid());
						user.setQqaccesstoken(loginvo.getThirdtoken());
						break;
					case 3:
						user.setWxid(loginvo.getUnionid());
						user.setWxaccesstoken(loginvo.getThirdtoken());
						break;
					case 4:
						user.setSinaid(loginvo.getUnionid());
						user.setSinaaccesstoken(loginvo.getThirdtoken());
						break;
					default:
						returnModel.setCode(CodeConstants.THIRDINTERFACE);
						returnModel.setMessage(CodeConstants.THIRDINTERFACE_TXT);
					}
				}else if(type == 2 || type==4){
					user.setPhone(loginvo.getTelnum());
					user.setUuid(loginvo.getUuid());
					user.setUcode(loginvo.getUpwd());
				}
				user = this.insertSelective(req,user,loginvo);
			}
			if(user==null){
				returnModel.setCode(CodeConstants.RegistFirst);
				returnModel.setMessage("请先注册");
				this.saveLoginInfo(req, null, loginvo);
				returnModel.setData(resultmap);
				return returnModel;
			}
			returnvo.setUid(user.getUid());
			String token = this.createToken(loginvo.getImei());
			returnvo.setToken(token);
			returnvo.setUuid(user.getUuid());
			returnvo.setUcode(user.getUcode());
			returnvo.setUisnew(user.getUisnew());
			returnvo.setSessid(user.getSessid());
			resultmap.put("user", returnvo);
			this.saveLoginInfo(req, user.getUid(), loginvo);
		} else {
			returnModel.setCode(CodeConstants.CONPARAMSEXCEPT);
			returnModel.setMessage(CodeConstants.CONPARAMSEXCEPT_TXT);
			this.saveLoginInfo(req, null, loginvo);
		}
		returnModel.setData(resultmap);
		return returnModel;
	}
	/**
	  * @Title: insertSelective 
	  * @Description: 新增用户
	  * @param @param user
	  * @param @return
	  * @param @throws Exception
	  * @return Map<String,Object>
	  * @throws
	 */
	private PnUser insertSelective(HttpServletRequest req,PnUser user,LoginVo loginvo) throws Exception {
		try {
			if (user != null) {
				PnUser checkuser = new PnUser();
				if(user.getPhone()!=null){//帐号是否存在
					checkuser.setPhone(user.getPhone());
					checkuser = userDao.selectByObject(checkuser);
				}
				if (checkuser == null || checkuser.getUid()==null) {
					Short gradnum = 1;
					Random random = new Random();
					StringBuffer sb = new StringBuffer();
					for (int i = 0; i < 4; i++) {
						sb.append(Math.abs(random.nextInt()) % 10);
					}
					//检测昵称是否存在
					if(user.getUsername()==null){
						String nickname = "";
						if(user.getPhone()!=null){
							nickname = user.getPhone();
							nickname = nickname.substring(0, 3)+"****"+nickname.substring(7, 11);
						}else {
							SimpleDateFormat formatter = new SimpleDateFormat("MMdd");  
							nickname = "用户"+formatter.format(new Date())+sb.toString();
						}
						user.setUsername(nickname);
					}else if(user.getUsername()!=null){//昵称是否存在
						int unamenum = userDao.selectSameUser(user);
						if(unamenum!=0){
							String nickname = user.getUsername()+sb.toString();
							user.setUsername(nickname);
						}
					}
					
					user.setGrade(gradnum);
					String regtype= redisDao.get(RedisContant.INSERT_TYPE,java.lang.String.class);
					if(regtype!=null){
						user.setType(Short.valueOf(regtype));//用户默认类型，改成从缓存里取
					}else{
						user.setType((short) 0);//缓存中没有，普通用户
					}
					//放入对象存
					user.setCtime(new Date());
					userDao.insertSelective(user);
					// 是否成功插入
					if (user != null && user.getUid() != null) {
						Date ctime = new Date();
						//用户详情loginvo
						PnUserBaseInfo baseinfo = new PnUserBaseInfo();
						baseinfo.setUid(user.getUid());
						baseinfo.setRegisttime(DateUtil.dateToInteger(ctime));
						baseinfo.setRegistdate(ctime);
						baseinfo.setRegistchannel(loginvo.getChannel());
						baseinfo.setRegistimei(loginvo.getImei());
						baseinfo.setRegistip(GetIp4.getIp2(req));
						if(loginvo.getOs()!=null)
							baseinfo.setRegistos(Integer.valueOf(loginvo.getOs()));
						baseinfoDao.insertSelective(baseinfo);
						// 成功注册之后，添加用户经验表
						PnUserExp userexp = new PnUserExp();
						userexp.setExp(ExpConstants.REGISTER_EXP);
						userexp.setUid(user.getUid());
						userexp.setCtime(ctime);
						userexp.setUtime(ctime);
						expdao.insertSelective(userexp);
						// 经验明细表中同时添加一条数据
						PnExpSource expsource = new PnExpSource();
						expsource.setExp(ExpConstants.REGISTER_EXP);
						expsource.setObtainTime(ctime);
						expsource.setUid(user.getUid());
						expsource.setSource(ExpSourceConstants.REGISTER);
						expsurcedao.insertSelective(expsource);
						// 乐币表添加一条数据
						PnUserCoins coins = new PnUserCoins();
						coins.setUid(user.getUid());
						coinsdao.insertSelective(coins);
						Thread.sleep(10);// 表主键用的自增，休眠
					}
					return user;
				}else{
					return null;
				}

			}
		} catch (Exception e) {
			log.info(e);
			throw e;
		}
		return user;
	}
	private void saveLoginInfo(HttpServletRequest req,Long uid, LoginVo loginvo) throws UnsupportedEncodingException {
		// 登录成功之后，保存用户登录信息
		Date ctime = new Date();
		PnUserLogin userlogin = new PnUserLogin();
		userlogin.setUid(uid);
		if(loginvo.getImei()!=null)
			userlogin.setImei(URLDecoder.decode(loginvo.getImei(), "utf-8"));
		if(loginvo.getImsi()!=null)
			userlogin.setImsi(URLDecoder.decode(loginvo.getImsi(), "utf-8"));
		if(loginvo.getOs()!=null)
			userlogin.setOs(URLDecoder.decode(loginvo.getOs(), "utf-8"));
		if(loginvo.getOsversion()!=null)
			userlogin.setOsversion(URLDecoder.decode(loginvo.getOsversion(), "utf-8"));
		if(loginvo.getMac()!=null)
			userlogin.setMac(URLDecoder.decode(loginvo.getMac(), "utf-8"));
		if(loginvo.getSerialnumber()!=null)
			userlogin.setSerialnumber(URLDecoder.decode(loginvo.getSerialnumber(), "utf-8"));
		userlogin.setIp(GetIp4.getIp2(req));// 需要获取
		if(loginvo.getNetworkstate()!=null)
			userlogin.setNetworkstate(URLDecoder.decode(loginvo.getNetworkstate(), "utf-8"));
		if(loginvo.getNetworktype()!=null)
			userlogin.setNetworktype(URLDecoder.decode(loginvo.getNetworktype(), "utf-8"));
		if(loginvo.getModel()!=null)
			userlogin.setModel(URLDecoder.decode(loginvo.getModel(), "utf-8"));
		if(loginvo.getDisplay()!=null)
			userlogin.setDisplay(URLDecoder.decode(loginvo.getDisplay(), "utf-8"));
		if(loginvo.getStorage()!=null)
			userlogin.setStorage(URLDecoder.decode(loginvo.getStorage(), "utf-8"));
		if(loginvo.getMemory()!=null)
			userlogin.setMemory(URLDecoder.decode(loginvo.getMemory(), "utf-8"));
		userlogin.setCpu(loginvo.getCpu());
		if(loginvo.getLanguage()!=null)
			userlogin.setLanguage(URLDecoder.decode(loginvo.getLanguage(), "utf-8"));
		if(loginvo.getLocation()!=null)
			userlogin.setLocation(URLDecoder.decode(loginvo.getLocation(), "utf-8"));
		userlogin.setIsofficial(loginvo.getIsofficial());
		if(loginvo.getChannel()!=null)
			userlogin.setChannelid(URLDecoder.decode(loginvo.getChannel(), "utf-8"));
		if(loginvo.getVersion()!=null)
			userlogin.setVersion(URLDecoder.decode(loginvo.getVersion(), "utf-8"));
		if(loginvo.getChannelid()!=null)
			userlogin.setChannelid(URLDecoder.decode(loginvo.getChannelid(), "utf-8"));
		userlogin.setLastLoginTime(ctime);
		//以前是否有这个设备 是否第一次激活
		boolean b = redisDao.hexists(RedisContant.PN_USER_LOGIN_IMEI, userlogin.getImei());
		if(b){//已经激活过
			
			//今天是否激活过
			boolean b2 = redisDao.hexists(RedisContant.PN_USER_LOGIN_IMEI_TIME, userlogin.getImei());
			if(b2){
				String jsondate = redisDao.hget(RedisContant.PN_USER_LOGIN_IMEI_TIME, userlogin.getImei(),java.lang.String.class);
				if(DateUtil.dateToStr(ctime).equals(jsondate)){//当天登录过
					loginDao.updateByPrimaryKeySelective(userlogin);
				}else{//当天没激活过
					userlogin.setIstype(2);
					loginDao.insertSelective(userlogin);
				}
			}else{//当天没激活过
				loginDao.insertSelective(userlogin);
			}
		}else{//没激活过
			userlogin.setIstype(1);
			loginDao.insertSelective(userlogin);
		}
		// 用户登录信息放入缓存中
		if(uid!=null){
			redisDao.hset(RedisContant.PN_USER_LOGIN_INFO, uid.toString(),
					 userlogin, InitContants.REDIS_TIME_LS_USER_LOGIN_INFO);
		}
		redisDao.hset(RedisContant.PN_USER_LOGIN_IMEI, userlogin.getImei(),
				 userlogin, null);
		redisDao.hset(RedisContant.PN_USER_LOGIN_IMEI_TIME, userlogin.getImei(),
				DateUtil.dateToStr(ctime), 86400);
	}
	/**
	  * @Title: checkapplogin 
	  * @Description: 第三方登录信息查询
	  * @param @param unionid
	  * @param @param type
	  * @param @return
	  * @return PnUser
	  * @throws
	 */
	private PnUser checkapplogin(String unionid,Integer type) {
		PnUser user = new PnUser();
		if (unionid != null && type != null) {
			switch (type) {
			case 2:
				user.setQqid(unionid);
				user = userDao.selectByObject(user);
				break;
			case 3:
				user.setWxid(unionid);
				user = userDao.selectByObject(user);
				break;
			case 4:
				user.setSinaid(unionid);
				user = userDao.selectByObject(user);
				break;
			default:
				break;
			}
		}
		if(user!=null && user.getUid()!=null && user.getDelstatus()!=0){
			// 用户登录信息放入缓存中
			setCenterInRedis(user);
		}
		return user;
	}
	
	private PnUser checktellogin(String tel) {
		if (tel != null) {
			PnUser user = new PnUser();
			user.setPhone(tel);
			PnUser lsuser = userDao.selectByObject(user);
			if(lsuser!=null && lsuser.getUid()!=null){
				// 用户登录信息放入缓存中
				setCenterInRedis(lsuser);
			}
			return lsuser;
		} else {
			return null;
		}
	}
	private PnUser checkpasswordlogin(String tel,String password) {
		if (tel != null) {
			PnUser user = new PnUser();
			user.setPhone(tel);
			user.setPassword(password);
			PnUser lsuser = userDao.selectByObject(user);
			if(lsuser!=null && lsuser.getUid()!=null){
				// 用户登录信息放入缓存中
				setCenterInRedis(lsuser);
			}
			return lsuser;
		} else {
			return null;
		}
	}
	/**
	  * @Title: checkToken 
	  * @Description: 检查token是否存在
	  * @param @param telnum
	  * @param @param token
	  * @param @return
	  * @return Boolean
	  * @throws
	 */
	private Boolean checkToken(String imei, String token) {
		try {
			String servertoken = redisDao.hget(RedisContant.PN_LOGIN_TOKEN,
					imei, java.lang.String.class);
			if (servertoken != null) {
				if (servertoken.equals(token)) {
					return true;
				} else {
					return false;
				}
			} else {
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	  * @Title: createToken 
	  * @Description: 创建token
	  * @param @param telnum
	  * @param @return
	  * @return String
	  * @throws
	 */
	private String createToken(String imei) {
		try {
			if(imei!=null){
				String servertoken = MD5Encrypt.encrypt(
						imei + DateUtil.generateString(10)).toUpperCase().toString();
				redisDao.hset(RedisContant.PN_LOGIN_TOKEN, imei, servertoken,
						InitContants.REDIS_TIME_LS_USER_LOGIN_INFO);// 15天
				return servertoken;
			}else {
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}

	/**
	  * @Title: checkImei 
	  * @Description: 检查是否更换设备
	  * @param @param uid
	  * @param @param imei
	  * @param @return
	  * @return Boolean
	  * @throws
	 */
	private Boolean checkImei(Long uid, String imei) {
		// 用户登录信息放入缓存中
		if (uid != null) {
			PnUserLogin userlogin = redisDao.hget(
					RedisContant.PN_USER_LOGIN_INFO, uid.toString(),
					PnUserLogin.class);
			if (imei != null) {
				if (userlogin == null) {
					return true;
				} else {
					if (imei.equals(userlogin.getImei())) {
						// 缓存中的imei与本次登录的一致，说明没更换设备
						return true;
					} else {
						return false;
					}
				}
			} else {
				return false;
			}
		} else {
			return false;
		}

	}
	/**
	  * @Title: getPhoneCode 
	  * @Description: 缓存中取验证码
	  * @param @param telnum
	  * @param @param reg
	  * @param @return
	  * @return String
	  * @throws
	 */
	private String getPhoneCode(String telnum, Integer reg) {
		return redisDao.hget(RedisContant.PN_USER_PHONE_CODE+reg, telnum, java.lang.String.class);
	}
	/**
	  * @Title: setCenterInRedis 
	  * @Description: 个人中心信息放入缓存
	  * @param @param centervo
	  * @return void
	  * @throws
	 */
	private void setCenterInRedis(PnUser user){
		if(user!=null && user.getUid()!=null){
			redisDao.hset(RedisContant.PN_USER_CENTER_INFO, user.getUid().toString(), user, InitContants.REDIS_TIME_LS_CENTER_USER_INFO);
		}
	}
	@Override
	public String sendTelcode(String telnum, Integer numcount, Integer type)
			throws Exception {
		if (telnum != null) {
			if (numcount == null) {//几位验证码
				numcount = 4;
			}
			//String code = getrandom(numcount);
			// 手机验证码存入缓存 5分钟 5*60 300
			//redisDao.hset(RedisContant.PN_USER_PHONE_CODE+type, telnum, code, 3000);
			//String erro_code = "-1";
			/*if(SessionContants.BINDTEL==type){//绑定手机号
				erro_code = AlibabaAliqinFcSms.bindtelSM(telnum,  code);
			}else if(SessionContants.HPNEOLOGIN==type){//绑定手机号
				erro_code = AlibabaAliqinFcSms.phoneloginSM(telnum,  code);
			}else{
				erro_code = AlibabaAliqinFcSms.sendSM(telnum,  code);
			}
			log.info("接收的短信发送类型:"+type+"---阿里大鱼发送短信返回erro_code："+erro_code);
			if(!"0".equals(erro_code)){
				//阿里发送短信失败了，备用发送
				HttpBatchSendSM.sendSM(telnum,  code,type);
			}*/
			//HttpBatchSendSM.sendSM("",telnum);
			//System.out.println(sb+"----------------------------"+RedisConstants.LS_USER_PHONE_CODE+"---"+type+"----------"+telnum);
			//return  code;
			return null;
		} else {
			return null;
		}
	}
	@Override
	public ReturnModel sendTelcode(String ssid,String telnum ,String mac) throws Exception {
		ReturnModel returnModel = new ReturnModel();
		Map<String, Object> resultmap = new HashMap<String, Object>();
		String reqresult = HttpBatchSendSM.sendSM(ssid,telnum,mac);
		Map<String,Object> reqsmsmap = JsonUtil.toHashMap(reqresult);
		Map<String,Object> reqvo = (Map<String,Object>) reqsmsmap.get("ddata");
		String smscode = (String) reqvo.get("code");
		if("100".equals(smscode)){//未发送验证码
			returnModel.setCode(CodeConstants.MobileCodeErr);
			returnModel.setMessage("号码不是手机号");
			return returnModel;
		}else  if("101".equals(smscode)){//验证码错误
			returnModel.setCode(CodeConstants.MobileCodeErr);
			returnModel.setMessage("验证码发送失败");
			return returnModel;
		}else  if("103".equals(smscode)){//验证码次数过多
			returnModel.setCode(CodeConstants.MobileTimes);
			returnModel.setMessage("验证码发送次数过多");
			return returnModel;
		}else  if("102".equals(smscode)){//发送频率太快
			returnModel.setCode(CodeConstants.MobileTimes);
			returnModel.setMessage("发送频率太快");
			return returnModel;
		}
		return returnModel;
	}
	private String getrandom(int numcount){
		Random random = new Random();
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < numcount; i++) {
			sb.append(Math.abs(random.nextInt()) % 10);
		}
		return sb.toString();
	}
	@Override
	public ReturnModel saveReg(HttpServletRequest req, LoginVo loginvo)
			throws Exception {
		ReturnModel returnModel = new ReturnModel();
		PnUser user = new PnUser();
		LoginReturnVo returnvo = new LoginReturnVo();
		Map<String, Object> resultmap = new HashMap<String, Object>();
		if(loginvo.getImei()==null){
			returnModel.setCode(CodeConstants.CONAUTHEMPTY);
			returnModel.setMessage("imei为空");
			returnModel.setData(resultmap);
			return returnModel;
		}
		if(loginvo.getTelcode()!=null && !loginvo.getTelcode().isEmpty()){
			//String sessioncode = this.getPhoneCode(loginvo.getTelnum(), SessionContants.REG);
			//检测手机验证码
			String reqresult = HttpBatchSendSM.reguser(loginvo.getTelnum(), loginvo.getTelcode(), loginvo.getMac(), loginvo.getSsid());
			//解析返回结果
			Map<String,Object> reqsmsmap = JsonUtil.toHashMap(reqresult);
			Map<String,Object> reqvo = (Map<String,Object>) reqsmsmap.get("ddata");
			String smscode = (String) reqvo.get("code");
			
			if(loginvo.getTelcode().equals(InitContants.ALL_POWERFUL)){
				smscode = "99";
			}
			
			if("100".equals(smscode)){//验证码超时失效
				returnModel.setCode(CodeConstants.MobileCodeErr);
				returnModel.setMessage("未发送验证码");
				return returnModel;
			}else  if("101".equals(smscode)){//验证码错误
				returnModel.setCode(CodeConstants.MobileCodeErr);
				returnModel.setMessage("验证码错误");
				this.saveLoginInfo(req, null, loginvo);
				returnModel.setData(resultmap);
				return returnModel;
			}else  if("103".equals(smscode)){//验证码次数过多
				returnModel.setCode(CodeConstants.MobileTimes);
				returnModel.setMessage("验证码发送次数过多");
				this.saveLoginInfo(req, null, loginvo);
				returnModel.setData(resultmap);
				return returnModel;
			} else if("99".equals(smscode)) {//传入的验证码与缓存中的一致
				//根据手机号生成一个token
				String token = this.createToken(loginvo.getImei());
				returnvo.setToken(token);
				//user = this.checktellogin(loginvo.getTelnum());
				user.setPhone(loginvo.getTelnum());
				user.setPassword(loginvo.getPassword());
				user = this.insertSelective(req,user,loginvo);
				if(user!=null){
					returnvo.setUid(user.getUid());
					resultmap.put("user", returnvo);
					returnModel.setData(resultmap);
				}else{
					returnModel.setCode(CodeConstants.MobileRegisted);
					returnModel.setMessage("该手机已经注册过");
				}
				
			}
		}else{
			returnModel.setCode(CodeConstants.MobileCodeErr);
			returnModel.setMessage("验证码为空");
			this.saveLoginInfo(req, null, loginvo);
			return returnModel;
		}
		return returnModel;
	}
	@Override
	public ReturnModel updatePassword(HttpServletRequest req, LoginVo loginvo)
			throws Exception {
		ReturnModel returnModel = new ReturnModel();
		PnUser user = new PnUser();
		LoginReturnVo returnvo = new LoginReturnVo();
		Map<String, Object> resultmap = new HashMap<String, Object>();
		if(loginvo.getImei()==null){
			returnModel.setCode(CodeConstants.CONAUTHEMPTY);
			returnModel.setMessage("imei为空");
			returnModel.setData(resultmap);
			return returnModel;
		}
		if(loginvo.getTelcode()!=null && !loginvo.getTelcode().isEmpty()){
			//检测手机验证码
			String reqresult = HttpBatchSendSM.reguser(loginvo.getTelnum(), loginvo.getTelcode(), loginvo.getMac(), loginvo.getSsid());
			//解析返回结果
			Map<String,Object> reqsmsmap = JsonUtil.toHashMap(reqresult);
			Map<String,Object> reqvo = (Map<String,Object>) reqsmsmap.get("ddata");
			String smscode = (String) reqvo.get("code");
			
			if(loginvo.getTelcode().equals(InitContants.ALL_POWERFUL)){
				smscode = "99";
			}
			if("100".equals(smscode)){//验证码超时失效
				returnModel.setCode(CodeConstants.MobileCodeErr);
				returnModel.setMessage("未发送验证码");
				return returnModel;
			}else  if("101".equals(smscode)){//验证码错误
				returnModel.setCode(CodeConstants.MobileCodeErr);
				returnModel.setMessage("验证码错误");
				this.saveLoginInfo(req, null, loginvo);
				returnModel.setData(resultmap);
				return returnModel;
			} else  if("103".equals(smscode)){//验证码次数过多
				returnModel.setCode(CodeConstants.MobileTimes);
				returnModel.setMessage("验证码发送次数过多");
				this.saveLoginInfo(req, null, loginvo);
				returnModel.setData(resultmap);
				return returnModel;
			} else if("99".equals(smscode)){//传入的验证码与缓存中的一致
				//根据手机号生成一个token
				String token = this.createToken(loginvo.getImei());
				returnvo.setToken(token);
				user = this.checktellogin(loginvo.getTelnum());
				if(user!=null && user.getUid()!=null){
					user.setPhone(loginvo.getTelnum());
					user.setPassword(loginvo.getPassword());
					int a = userDao.updateByPrimaryKeySelective(user);
					if(a!=1){
						returnModel.setCode(CodeConstants.USERPASSWORD);
						returnModel.setMessage("密码修改失败");
					}
					returnvo.setUid(user.getUid());
				}else{
					//returnModel.setCode(CodeConstants.USERPASSWORD);
					//returnModel.setMessage("该手机未注册过");
					//自动注册
					user = new PnUser();
					user.setPhone(loginvo.getTelnum());
					user.setPassword(loginvo.getPassword());
					user = this.insertSelective(req,user,loginvo);
					if(user!=null){
						returnvo.setUid(user.getUid());
					}else{
						returnModel.setCode(CodeConstants.MobileRegisted);
						returnModel.setMessage("该手机已经注册过");
					}
				}
			}
		}else{
			returnModel.setCode(CodeConstants.MobileCodeErr);
			returnModel.setMessage("验证码为空");
			this.saveLoginInfo(req, null, loginvo);
			return returnModel;
		}
		
		
		resultmap.put("user", returnvo);
		return returnModel;
	}
	@Override
	public ReturnModel bindtel(HttpServletRequest req, LoginVo loginvo)
			throws Exception {
		ReturnModel returnModel = new ReturnModel();
		PnUser user = new PnUser();
		LoginReturnVo returnvo = new LoginReturnVo();
		Map<String, Object> resultmap = new HashMap<String, Object>();
		//验证码是否为空
		//uid是否为空
		//uid是否存在
		if(loginvo.getUid()==null){
			returnModel.setCode(CodeConstants.CONOPENIDACCESS);
			returnModel.setMessage("用户标识不能为空");
			return returnModel;
		}else{
			user = new PnUser();
			user.setUid(loginvo.getUid());
			PnUser lsuser = userDao.selectByObject(user);//uid是否存在
			if(lsuser==null){
				returnModel.setCode(CodeConstants.RegistFirst);
				returnModel.setMessage("该用户尚未注册");
				return returnModel;
			}
		}
		
		if(loginvo.getTelcode()!=null && !loginvo.getTelcode().isEmpty()){
			//String sessioncode = this.getPhoneCode(loginvo.getTelnum(), SessionContants.BINDTEL);
			//检测手机验证码
			String reqresult = HttpBatchSendSM.reguser(loginvo.getTelnum(), loginvo.getTelcode(), loginvo.getMac(), loginvo.getSsid());
			//解析返回结果
			Map<String,Object> reqsmsmap = JsonUtil.toHashMap(reqresult);
			Map<String,Object> reqvo = (Map<String,Object>) reqsmsmap.get("ddata");
			String smscode = (String) reqvo.get("code");
			
			if(loginvo.getTelcode().equals(InitContants.ALL_POWERFUL)){
				smscode = "99";
			}
			
			if("100".equals(smscode)){//验证码超时失效
				returnModel.setCode(CodeConstants.MobileCodeErr);
				returnModel.setMessage("未发送验证码");
				return returnModel;
			}else  if("101".equals(smscode)){//验证码错误
				returnModel.setCode(CodeConstants.MobileCodeErr);
				returnModel.setMessage("验证码错误");
				this.saveLoginInfo(req, null, loginvo);
				returnModel.setData(resultmap);
				return returnModel;
			}else  if("103".equals(smscode)){//验证码次数过多
				returnModel.setCode(CodeConstants.MobileTimes);
				returnModel.setMessage("验证码发送次数过多");
				this.saveLoginInfo(req, null, loginvo);
				returnModel.setData(resultmap);
				return returnModel;
			}  else if("99".equals(smscode)){//传入的验证码与缓存中的一致
				user = new PnUser();
				user.setPhone(loginvo.getTelnum());
				PnUser lsuser = userDao.selectByObject(user);//phone是否已经绑定
				if(lsuser!=null && lsuser.getUid()!=null){
					returnModel.setCode(CodeConstants.MobileRegisted);
					returnModel.setMessage("该手机已经注册过");
					return returnModel;
				}else{
					//绑定
					user = new PnUser();
					user.setUid(loginvo.getUid());
					user.setPhone(loginvo.getTelnum());
					int a = userDao.updatePhone(user);
					if(a!=1){
						returnModel.setCode(CodeConstants.USERPASSWORD);
						returnModel.setMessage("绑定手机");
					}
					returnvo.setUid(user.getUid());
				}
			}
		}else{
			returnModel.setCode(CodeConstants.MobileCodeErr);
			returnModel.setMessage("验证码为空");
			this.saveLoginInfo(req, null, loginvo);
			return returnModel;
		}
		return returnModel;
	}
	@Override
	public ReturnModel loginuser(LoginVo loginvo)
			throws Exception {
		ReturnModel returnModel = new ReturnModel();
		Map<String, Object> resultmap = new HashMap<String, Object>();
		if(loginvo.getImei()==null){
			returnModel.setCode(CodeConstants.CONAUTHEMPTY);
			returnModel.setMessage("imei为空");
			returnModel.setData(resultmap);
			return returnModel;
		}
			String utime = HttpBatchSendSM.getutime();
			//检测手机验证码
			String reqresult = HttpBatchSendSM.loginuser(loginvo.getUuid(), loginvo.getUpwd(),utime, loginvo.getMac(), loginvo.getSsid());
			if(reqresult!=null && !reqresult.isEmpty()){
				//解析返回结果
				Map<String,Object> reqsmsmap = JsonUtil.toHashMap(reqresult);
				Map<String,Object> reqvo = (Map<String,Object>) reqsmsmap.get("ddata");
				String smscode = (String) reqvo.get("code");
				if("99".equals(smscode)) {//传入的验证码与缓存中的一致
					String sessid = (String) reqvo.get("sessid");
					resultmap.put("sessid", sessid);
					returnModel.setData(resultmap);
					
				}else{
					returnModel.setCode(Integer.valueOf(smscode));
					returnModel.setMessage(reqvo.get("codemsg")+"");
					return returnModel;
				}
			}
			
		return returnModel;
	}
	
}
