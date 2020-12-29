package wolfman.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import dolphin.core.Pair;
import dolphin.uuid.TokenId;
import org.apache.log4j.Logger;
import redis.clients.jedis.*;
import wolfman.async.AsyncManager;
import wolfman.core.cache.redis.RedisBucket;
import wolfman.core.cache.redis.RedisKeyPrefix;
import wolfman.core.cluster.ZkService;
import wolfman.core.utils.CheckUtils;
import wolfman.dictionary.ContestConfig;
import wolfman.dictionary.lib.*;
import wolfman.hall.domain.RoomInterface;
import wolfman.room.domain.ReviewVo;
import wolfman.room.domain.UserInfoVO;
import wolfman.room.state.Dateutils;
import wolfman.services.domain.UserInfo;
import wolfman.web.dao.UserDao;
import wolfman.web.dao.UserLoginDao;
import wolfman.web.dao.domain.*;
import wolfman.web.db.ConstantDefine;
import wolfman.web.db.domain.UserAccountModel;
import wolfman.web.db.domain.UserInfoModel;
import wolfman.web.db.util.AesUtil;
import wolfman.web.db.util.ZipUtil;
import wolfman.web.els.domain.UserSearchVo;
import wolfman.web.handler.utils.CodeContant;
import wolfman.web.services.RelationRedisService;
import wolfman.web.sync.UserIndexer;
import wolfman.web.vo.BindRoomVo;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserService extends BaseService implements RedisKeyPrefix {

    private final static Logger LOG = Logger
            .getLogger(UserService.class);

    private final static UserService instance = new UserService();
    private final static String Joy_Uid_Key = "faBt1IxxFTzIGPGt";
    private final static int EXPIRE_DURATION = 12 * 3600;
    private final static int USER_INFO_SAVE = 7 * 24 * 3600;
    private final static int USER_INFO_SAVE_Contest = 365 * 24 * 3600;
    private final static int RECORD_INFO_SAVE = 24 * 3600;
    private final static long ZEGO_APPID = 827737874;
    public static final String ZEGO_SIGNKEY = "0x8b,0x8d,0x01,0x44,0xdd,0xe9,0x89,0x48,0xf7,0x21,0x2f,0x3d,0x3c,0xa8,0x08,0x6e,0xbb,0xf6,0x07,0x58,0x96,0x9c,0x3d,0xf1,0x1e,0x54,0xd4,0xd7,0xf9,0x2e,0x61,0x45";

    public static UserService getInstance() {
        return instance;
    }

    /**
     * 获取映射用户所在的comet服务器
     *
     * @param clientId
     * @return
     */
    public String whichCometByClientId(String clientId) {
        Jedis jedis = jedisNoShardPool.getResource();
        try {
            return jedis.hget(RedisKeyPrefix.RouteUserKey, clientId);
        } catch (Exception ex) {
            LOG.error(String.format("whichCometByClientId>>clientId:%s", clientId), ex);
        } finally {
            jedis.close();
        }
        return null;
    }


    /**
     * 映射用户到指定的comet服务器
     *
     * @param clientId
     * @param serverId
     * @return
     */
    public Long routeUserToSomeComet(String clientId, String serverId) {
        Jedis jedis = jedisNoShardPool.getResource();
        try {
            return jedis.hset(RedisKeyPrefix.RouteUserKey, clientId, serverId);
        } catch (Exception ex) {
            LOG.error(String.format("routeUserToSomeComet>>clientId:%s,serverId:%s", clientId, serverId), ex);
        } finally {
            jedis.close();
        }
        return Long.valueOf(-1);
    }


    /**
     * 删除映射用户到指定的comet服务器
     *
     * @param clientId
     * @param serverId
     * @return
     */
    public Long routeUserToNullComet(String clientId, String serverId) {
        Jedis jedis = jedisNoShardPool.getResource();
        try {
            String cometId = whichCometByClientId(clientId);
            if (cometId != null && cometId.equals(serverId)) {
                return jedis.hdel(RedisKeyPrefix.RouteUserKey, clientId, serverId);
            }
        } catch (Exception ex) {
            LOG.error(String.format("routeUserToNullComet>>clientId:%s,serverId:%s", clientId, serverId), ex);
        } finally {
            jedis.close();
        }
        return Long.valueOf(-1);
    }

    public String getClientId(String token) {
        Jedis jedis = jedisNoShardPool.getResource();
        String dackey = String.format(RedisKeyPrefix.UserTokenKey, token);
        try {
            return jedis.get(dackey);
        } catch (Exception ex) {
            LOG.error(String.format("getClientId>>token:%s,dackey:%s", token, dackey), ex);
            return null;
        } finally {
            jedis.close();
        }
    }

    /**
     * 获取账户信息
     *
     * @param uid
     * @return
     */
    public boolean existUserInfo(String uid) {
        Jedis jedis = null;
        try {
            JedisPool jedisPool = RedisBucket.getInstance().getNoShardPool("user");
            jedis = jedisPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserInfoKey, uid);
            try {
                boolean b = jedis.exists(uidkey);
                if (!b) {
                    UserInfoModel userInfoModel = UserDao.getInstance().getUserInfo(uid);
                    if (userInfoModel != null) {
                        jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(userInfoModel));
                    } else {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                LOG.error("existUserInfo>>", e);
            }
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return false;
    }

    /**
     * 获取账户信息
     *
     * @param uid
     * @return
     */
    public UserInfoModel getUserInfo(String uid) {
        UserInfoModel userInfoModel = new UserInfoModel();
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserInfoKey, uid);
            try {
                String userInfo = jedis.get(uidkey);
                userInfoModel = JSON.parseObject(userInfo, UserInfoModel.class);
                if (userInfoModel == null) {
                    userInfoModel = UserDao.getInstance().getUserInfo(uid);
                    //set redis
                    if (userInfoModel != null)
                        jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(userInfoModel));

                }
                if (userInfoModel != null) {
                    int lv = LevelConfigLib.getSuitableLevel(userInfoModel.getExp());
                    userInfoModel.setLevel(lv);
                }
                return userInfoModel;
            } catch (Exception e) {
                LOG.error("getUserInfo>>", e);
            }
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return userInfoModel;
    }


    public UserInfo getClient(String clientId) {
        Jedis jedis = jedisNoShardPool.getResource();
        String jspair = null;
        try {
            jspair = jedis.get(clientId);
        } finally {
            jedis.close();
        }
        if (jspair == null) {
            return null;
        }
        UserInfo user = JSON.parseObject(jspair, UserInfo.class);
        user.setClientId(clientId);
        return user;
    }

    public void saveToken(String token, String uid) {
        Jedis jedis = jedisNoShardPool.getResource();
        try {
            String dackey = String.format(RedisKeyPrefix.UserTokenKey, token);
            jedis.setex(dackey, EXPIRE_DURATION, uid);
        } finally {
            jedis.close();
        }
    }

    public String getToken(String hex) {
        String uid = "";
        Jedis jedis = jedisNoShardPool.getResource();
        try {
            byte[] si = EncryptService.getInstance().fromHex(hex);
            String token = wolfman.services.EncryptService.getInstance().decryptByDefaultDes(si);
            String dackey = String.format(RedisKeyPrefix.UserTokenKey, token);
            uid = jedis.get(dackey);
        } finally {
            jedis.close();
        }
        return uid;
    }


    public Map<String, Object> doLogin(String code, String authkey, String accountname, Integer logintype, String nickname, String headpic,
                                       final String loginMethod, final String mobileModel, final String channel, final String mobileVersion,
                                       final Integer os, final String packageName) {
        //logintype 1:phone,2:app
        Map<String, Object> result = new HashMap<String, Object>(1);

        if (accountname == null && authkey == null) {
            return result;
        }
        if (headpic == null) {
            headpic = "";
        }

        final Jedis jedis = jedisNoShardPool.getResource();
        UserAccountModel accountModel = null;
        String uidkey = null;

        switch (logintype) {
            case 1://phone verify accountname,code
                //TODO:校验手机号格式长度
                //最小长度6位
                Pattern p = Pattern.compile("^[A-Za-z0-9-]{6,20}$");
                Matcher m = p.matcher(accountname);
                boolean isValid = m.matches();
                if (!isValid) {
                    result.put("reason", "accountname is error");
                    return result;
                }
                String truecode = "123456";
                if (code.equals(truecode)) {
                    //首先检查缓存
                    uidkey = String.format(RedisKeyPrefix.UserInfoPhoneKey, accountname);
                } else {
                    result.put("reason", "code is error");
                    return result;
                }
                break;
            case 2://app verify authkey
                if (authkey != null) {
                    //首先检查缓存
                    uidkey = String.format(RedisKeyPrefix.UserInfoAuthkeyKey, authkey);
                }
                break;
            default:
                LOG.error(String.format("doLogin no method defined for command logintype=%d", logintype));
                break;
        }
        try {
            String userInfo = jedis.get(uidkey);
            accountModel = JSON.parseObject(userInfo, UserAccountModel.class);
            if (accountModel == null) {
                accountModel = UserDao.getInstance().getUserAccount(authkey, accountname, logintype);
                //set redis
                if (accountModel != null)
                    jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(accountModel));
            }
        } finally {
            jedis.close();
        }
        int isfirstlogin = 0;//0不是第一次登录，1第一次登录
        //自动注册 账户数据以及用户数据
        if (accountModel == null) {
            isfirstlogin = 1;
            //insert account
            if (logintype == 1) {
                if (accountname != null && accountname.length() > 10) {
                    nickname = accountname.substring(0, 3) + "****" + accountname.substring(7, 11);
                } else {
                    nickname = accountname;
                }
            }
            try {
                accountModel = UserService.getInstance().insertModel(authkey, accountname, nickname, headpic, "0");
                final int uid = accountModel.getUid();
                AsyncManager.getInstance().execute(new Runnable() {
                    @Override
                    public void run() {
                        String uidkey = String.format(RedisKeyPrefix.UserLoginKey, uid + "_" + Dateutils.getlogindate());
                        jedis.setex(uidkey, 24 * 3600, String.valueOf(uid));
                        UserLoginDao.getInstance().addUserLogin(uid, loginMethod, mobileModel, channel, mobileVersion, os, 1, packageName);
                        ItemService.getInstance().addUserItem(String.valueOf(uid), ConstantDefine.GoodsConfig.normalRoomCardId, 3, 0);
                    }
                });
            } catch (Exception e) {
                LOG.error(String.format("insertModel error accountname=%d,logintype=%d", accountname, logintype));
            }

        } else {
            final int uid = accountModel.getUid();
            AsyncManager.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    //记录登录
                    //今天是否记录过
                    String uidkey = String.format(RedisKeyPrefix.UserLoginKey, uid + "_" + Dateutils.getlogindate());
                    String ul = jedis.get(uidkey);
                    if (ul == null) {//没有记录过才保存
                        jedis.setex(uidkey, 24 * 3600, String.valueOf(uid));
                        UserLoginDao.getInstance().addUserLogin(uid, loginMethod, mobileModel, channel, mobileVersion, os, 2, packageName);
                        ItemService.getInstance().addUserItem(String.valueOf(uid), ConstantDefine.GoodsConfig.normalRoomCardId, 3, 0);
                    }
                }
            });

        }

        result.put("uid", accountModel.getUid());
        result.put("accountid", accountModel.getAccountid());
        result.put("client_id", accountModel.getUid());
        result.put("isfirstlogin", isfirstlogin);

        String token = TokenId.nextId() + "#" + accountModel.getUid() + "#" + System.currentTimeMillis();
        byte[] si = wolfman.services.EncryptService.getInstance().encryptByDefaultDes(token);
        String hex = wolfman.services.EncryptService.getInstance().toHex(si);
//		UserService.getInstance().saveToken(token, accountModel.getUid().toString());
        result.put("token", hex);

        String matchtoken = TokenId.nextId() + "#" + accountModel.getUid() + "#" + accountModel.getAccountid() + "#" + System.currentTimeMillis();
        byte[] matchsi = wolfman.services.EncryptService.getInstance().encryptByDefaultDes(matchtoken);
        String matchhex = wolfman.services.EncryptService.getInstance().toHex(matchsi);
//		UserService.getInstance().saveMatchToken(matchtoken, accountModel.getUid().toString());

        //TODO:matchtoken
        result.put("matchToken", matchhex);

        return result;
    }

    /**
     * 注册用户
     *
     * @param authkey
     * @param accountname
     * @param nickname
     * @param headpic
     * @return
     */
    public UserAccountModel insertModel(String authkey, String accountname, final String nickname, String headpic, final String joyuid) {
        UserAccountModel userAccountModel = new UserAccountModel();
        int accountid = UserDao.getInstance().getDecorateId();
        final int uid = UserDao.getInstance().getUid();
        String password = "";
        if (accountname == null) {
            accountname = "qq" + uid;
        }
        if (authkey == null) {
            authkey = accountname;
        }
        if (headpic == null || headpic.isEmpty()) {
            String[] heads = {
                    "http://image.nextjoy.com/headpic/ae785d96318004d39ac61be0b7ea5eb4.png",
                    "http://image.nextjoy.com/headpic/674fc26331ebed1ae45defd17d0ec5f0.png",
                    "http://image.nextjoy.com/headpic/9e6c548ddf10dc2bfc53959f2be6e859.png",
                    "http://image.nextjoy.com/headpic/8fdc3689d79c414d807284ca9d30b309.png",
                    "http://image.nextjoy.com/headpic/99541715f1549ebf8b3760b822d62c02.png",
                    "http://image.nextjoy.com/headpic/8447575eaaddcb4b4f2372c54ac927e7.png",
            };
            Random random = new Random();
            int index = random.nextInt(heads.length);
            headpic = heads[index];
        }
        int i = UserDao.getInstance().addUserAccount(uid, accountid, password, authkey, accountname, joyuid);
        if (i != -1) {
            userAccountModel.setUid(uid);
            userAccountModel.setAccountid(accountid);
            String signature = "TA什么都没有留下";
            String seat = "摩羯座";
            String birthday = "1995-01-01";
            UserDao.getInstance().addUserInfo(uid, nickname, headpic, headpic, signature, seat, birthday);
            //TODO:异步生成用户信息表初始化数据
            AsyncManager.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    UserInfoModel userInfoModel = UserService.getInstance().getUserInfo(String.valueOf(uid));
                    UserSearchVo pair = new UserSearchVo(String.valueOf(uid), userInfoModel.getLuckId(), userInfoModel.getNickname(), userInfoModel.getGender(), userInfoModel.getHeadpicthumb(), userInfoModel.getLevel());
                    UserIndexer.getInstance().offerSaveQueue(pair, true);
                    UserDao.getInstance().addUserGameInfo(uid);
                    UserDao.getInstance().addUserCurrency(uid);
                    ItemService.getInstance().addUserItem(String.valueOf(uid), ConstantDefine.GoodsConfig.normalRoomCardId, 20, 0);
                }
            });
        }
        return userAccountModel;
    }

    public CenterVO getUserCenter(String uid) {
        Jedis jedis = null;
        try {
            String uidkey = String.format(RedisKeyPrefix.UserCenterKey, uid);
            jedis = jedisNoShardPool.getResource();
            String userInfo = jedis.get(uidkey);
            CenterVO centerVO = JSON.parseObject(userInfo, CenterVO.class);
            if (centerVO == null) {
                centerVO = UserDao.getInstance().getUserCenter(uid);
                //set redis
                if (centerVO != null) {
                    if (centerVO.getLv() == 0) {
                        int lv = LevelConfigLib.getSuitableLevel(centerVO.getExp());
                        centerVO.setLv(lv);
                    }
                    long maxexp = LevelConfigLib.getSuitableMaxexp(centerVO.getExp());
                    centerVO.setMaxexp(maxexp);

                    long crownMaxexp = LevelConfigLib.getSuitableCrownMaxexp(centerVO.getCrownexp());
                    centerVO.setCrownMaxexp(crownMaxexp);
                    //魅力
                    //成就

                    jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(centerVO));
                }

            }
            if (centerVO != null) {
                int lv = LevelConfigLib.getSuitableLevel(centerVO.getExp());
                centerVO.setLv(lv);
                if (centerVO.getMaxexp() == null) {
                    centerVO.setMaxexp(LevelConfigLib.getSuitableMaxexp(centerVO.getExp()));
                }
                if (centerVO.getCrownMaxexp() == null) {
                    centerVO.setCrownMaxexp(LevelConfigLib.getSuitableCrownMaxexp(centerVO.getCrownexp()));
                }
                //粉丝
                Long fanstotal = RelationRedisService.getInstance().getFansTotal(Integer.valueOf(uid));
                if (fanstotal != null) {
                    centerVO.setFansTotal(fanstotal.intValue());
                }
                //关注
                int followstotal = RelationRedisService.getInstance().getFollowsTotal(Integer.valueOf(uid));
                centerVO.setFollowsTotal(followstotal);
            }
            return centerVO;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public RecordVO getUserRecord(String uid) {
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserRecordKey, uid);
            String userInfo = jedis.get(uidkey);
            RecordVO recordVO = JSON.parseObject(userInfo, RecordVO.class);
            if (recordVO == null) {
                recordVO = UserDao.getInstance().getUserRecord(uid);
                //set redis
                if (recordVO != null)
                    jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(recordVO));
            }
            return recordVO;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public RecordVO getUserRecordContest(String uid) {
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserRecordContestKey, uid);
            String userInfo = jedis.get(uidkey);
            RecordVO recordVO = JSON.parseObject(userInfo, RecordVO.class);
            if (recordVO == null) {
                /*recordVO = UserDao.getInstance().getUserRecord(uid);
                //set redis
                if (recordVO != null)
                    jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(recordVO));*/
                recordVO = new RecordVO();
            }
            return recordVO;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public CurrencyVO getUserCurrency(String uid) {
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserCurrencyKey, uid);
            String userInfo = jedis.get(uidkey);
            CurrencyVO currencyVO = JSON.parseObject(userInfo, CurrencyVO.class);
            if (currencyVO == null) {
                currencyVO = UserDao.getInstance().getUserCurrency(uid);
                //set redis
                if (currencyVO != null)
                    jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(currencyVO));
            }
            return currencyVO;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public void addUserCurrencyBindDiamondnum(int uid, int diamondnum) {
        UserDao.getInstance().addUserCurrencyBindDiamondnum(uid, diamondnum);
        changeCurrencyVO(String.valueOf(uid));
    }

    public void addUserCurrencyDiamondnum(int uid, int diamondnum) {
        UserDao.getInstance().addUserCurrencyDiamondnum(uid, diamondnum);
        changeCurrencyVO(String.valueOf(uid));
    }

    public void addUserCurrencyGoldnum(int uid, int goldnum) {
        UserDao.getInstance().addUserCurrencyGoldnum(uid, goldnum);
        changeCurrencyVO(String.valueOf(uid));
    }

    public void addUserCurrencyGoldnumGift(int uid, int goldnum) {
        UserDao.getInstance().addUserCurrencyGoldnumGift(uid, goldnum);
        changeCurrencyVO(String.valueOf(uid));
    }

    public void addUserCharmNum(String uid, int charmnum, UserInfoModel userInfoModel, String senduid) {
        UserDao.getInstance().addUserCharmNum(uid, charmnum);
        reloadUserToRedis(uid);
        //魅力榜
        Jedis rankJedis = null;
        try {
            JedisPool rankJedisPool = RedisBucket.getInstance().getNoShardPool("rank");
            rankJedis = rankJedisPool.getResource();
            rankJedis.zadd(RedisKeyPrefix.RankCharmKey, userInfoModel.getCharmNum() + charmnum, uid);
            rankJedis.zincrby(RedisKeyPrefix.RankCharmKey + ":day:" + Dateutils.getlogindate(), charmnum, uid);
            //周榜月榜已经做好，坐等策划改需求☻
            rankJedis.zincrby(RedisKeyPrefix.RankCharmKey + ":weak:" + Dateutils.getDateWeaks(), charmnum, uid);
            rankJedis.zincrby(RedisKeyPrefix.RankCharmKey + ":month:" + Dateutils.getMonthDate(), charmnum, uid);
            //个人的榜单，每个收礼人都有一个排行榜
            String privateCharmKey = String.format(RedisKeyPrefix.RankprivateCtrlKey, uid);
            rankJedis.zincrby(privateCharmKey, charmnum, senduid);
            rankJedis.zincrby(privateCharmKey + ":day:" + Dateutils.getlogindate(), charmnum, senduid);
            //rankJedis.zincrby(privateCharmKey + ":weak:" + Dateutils.getDateWeaks(), charmnum, senduid);
            //rankJedis.zincrby(privateCharmKey + ":month:" + Dateutils.getMonthDate(), charmnum, senduid);
        } catch (Exception e) {
            LOG.error("addUserCharmNum>>>>>>", e);
        } finally {
            if (rankJedis != null) {
                rankJedis.close();
            }
        }

    }

    public void addUserContribution(String uid, int contribution, UserInfoModel userInfoModel) {
        UserDao.getInstance().addUserContribution(uid, contribution);
        reloadUserToRedis(uid);
        //贡献榜
        Jedis rankJedis = null;
        try {
            JedisPool rankJedisPool = RedisBucket.getInstance().getNoShardPool("rank");
            rankJedis = rankJedisPool.getResource();
            rankJedis.zadd(RedisKeyPrefix.RankContributionKey, userInfoModel.getContribution() + contribution, uid);
            rankJedis.zincrby(RedisKeyPrefix.RankContributionKey + ":day:" + Dateutils.getlogindate(), contribution, uid);
            rankJedis.zincrby(RedisKeyPrefix.RankContributionKey + ":weak:" + Dateutils.getDateWeaks(), contribution, uid);
            rankJedis.zincrby(RedisKeyPrefix.RankContributionKey + ":month:" + Dateutils.getMonthDate(), contribution, uid);
        } catch (Exception e) {
            LOG.error("addUserContribution>>>>>>", e);
        } finally {
            if (rankJedis != null) {
                rankJedis.close();
            }
        }
    }

    public void changeCurrencyVO(String uid) {
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserCurrencyKey, uid);
            CurrencyVO currencyVO = UserDao.getInstance().getUserCurrency(uid);
            //set redis
            if (currencyVO != null) {
                jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(currencyVO));
                HomeVO homeVO = getHomeVO(uid);
                if (homeVO != null) {
                    homeVO.setGoldnum(currencyVO.getGoldnum());
                    homeVO.setDiamond(currencyVO.getDiamondnum());
                    homeVO.setBinddiamond(currencyVO.getBinddiamondnum());
                    String homeuidkey = String.format(RedisKeyPrefix.HomeKey, uid);
                    jedis.setex(homeuidkey, RECORD_INFO_SAVE, JSON.toJSONString(homeVO));
                }
            }
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public Map<String, Object> getRecordInfoList(String uid, Integer start, Integer rows) {
        Map<String, Object> result = new HashMap<String, Object>(1);
        //TODO:分页
        List<RecordInfoVO> recordInfoVOList = new ArrayList<>();
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserRecordinfoKey, uid);
            LinkedHashSet<String> recordInfoVoSet = (LinkedHashSet<String>) jedis.zrevrange(uidkey, start, rows);
            for (String jo : recordInfoVoSet) {
                String json = ZipUtil.uncompress(jo);
                RecordInfoVO vo = JSON.parseObject(json, RecordInfoVO.class);
                if (vo.getRecordinfo() != null && !vo.getRecordinfo().isEmpty()) {
                    List<UserInfoVO> roomInfoList = JSONArray.parseArray(vo.getRecordinfo(), UserInfoVO.class);
                    vo.setUserInfoVOList(roomInfoList);
                    vo.setRecordinfo("");
                }
                if (vo.getReviewinfo() != null && !vo.getReviewinfo().isEmpty()) {
                    List<ReviewVo> reviewVoList = JSONArray.parseArray(vo.getReviewinfo(), ReviewVo.class);
                    vo.setReviewVoList(reviewVoList);
                    vo.setReviewinfo("");
                }
                recordInfoVOList.add(vo);
            }
            if (recordInfoVOList.size() == 0) {
                recordInfoVOList = UserDao.getInstance().getRecordInfoList(uid, start, rows);
                //json转list
                for (RecordInfoVO vo : recordInfoVOList) {
                    if (vo.getRecordinfo() != null && !vo.getRecordinfo().isEmpty()) {
                        List<UserInfoVO> roomInfoList = JSONArray.parseArray(vo.getRecordinfo(), UserInfoVO.class);
                        vo.setUserInfoVOList(roomInfoList);
                        vo.setRecordinfo("");
                    }
                    if (vo.getReviewinfo() != null && !vo.getReviewinfo().isEmpty()) {
                        List<ReviewVo> reviewVoList = JSONArray.parseArray(vo.getReviewinfo(), ReviewVo.class);
                        vo.setReviewVoList(reviewVoList);
                        vo.setReviewinfo("");
                    }
                    jedis.zadd(uidkey, vo.getPlaytime(), ZipUtil.compress(JSON.toJSONString(vo)));
                }
            }

            result.put("recordinfolist", recordInfoVOList);
            result.put("count", recordInfoVOList.size());
            if (recordInfoVOList.size() > 0) {
                if (recordInfoVOList.size() == rows + 1) {
                    result.put("nextPageStatus", true);
                } else {
                    result.put("nextPageStatus", false);
                }
            } else {
                result.put("nextPageStatus", false);
            }
            return result;
        } catch (Exception e) {
            LOG.error("getRecordInfoList>>>", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

        return result;
    }

    public Map<String, Object> getRecordInfoList_only(String uid, Integer start, Integer rows){
        return  getRecordInfoList_only(uid,start,rows,1);
    }

    public Map<String, Object> getRecordInfoList_only(String uid, Integer start, Integer rows,Integer type) {
        Map<String, Object> result = new HashMap<String, Object>(1);
        //TODO:分页
        List<RecordInfoVO> recordInfoVOList = new ArrayList<>();
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserRecordinfoKey_Only, uid);
            Set<Tuple> recordInfoVoSet = null;
            if (type == 1){
                recordInfoVoSet = jedis.zrevrangeWithScores(uidkey, start, rows);
            }else if (type == 2){//0全部 1普通 2天梯
                uidkey = String.format(RedisKeyPrefix.UserRecordinfoKey_Only_Contest, uid);
                recordInfoVoSet = jedis.zrevrangeWithScores(uidkey, start, rows);
            }else if (type == 0){
                recordInfoVoSet = jedis.zrevrangeWithScores(uidkey, start, rows);
                if (recordInfoVoSet!=null){
                    uidkey = String.format(RedisKeyPrefix.UserRecordinfoKey_Only_Contest, uid);
                    Set<Tuple> recordInfoVoSetC = jedis.zrevrangeWithScores(uidkey, start, rows);
                    recordInfoVoSet.addAll(recordInfoVoSetC);
                }

            }
            if (recordInfoVoSet == null){
                recordInfoVoSet = new LinkedHashSet<>();
                //recordInfoVoSet = jedis.zrevrangeWithScores(String.format(RedisKeyPrefix.UserRecordinfoKey_Only, uid), start, rows);
            }

            for (Tuple jo : recordInfoVoSet) {
                String json = ZipUtil.uncompress(jo.getElement());
                RecordInfoVO vo = JSON.parseObject(json, RecordInfoVO.class);
                if (vo.getRecordinfo() != null && !vo.getRecordinfo().isEmpty()) {
                    jedis.hset(uidkey + "ri", String.valueOf(vo.getRecordid()), ZipUtil.compress(vo.getRecordinfo()));
                }
                if (vo.getReviewinfo() != null && !vo.getReviewinfo().isEmpty()) {
                    jedis.hset(uidkey + "rw", String.valueOf(vo.getRecordid()), ZipUtil.compress(vo.getReviewinfo()));
                }
                vo.setRecordinfo("");
                vo.setReviewinfo("");
                recordInfoVOList.add(vo);
            }
            if (recordInfoVOList.size() == 0 && type == 1) {
                recordInfoVOList = UserDao.getInstance().getRecordInfoList(uid, start, rows);
                //json转list
                for (RecordInfoVO vo : recordInfoVOList) {
                    if (vo.getRecordinfo() != null && !vo.getRecordinfo().isEmpty()) {
                        jedis.hset(uidkey + "ri", String.valueOf(vo.getRecordid()), ZipUtil.compress(vo.getRecordinfo()));
                    }
                    if (vo.getReviewinfo() != null && !vo.getReviewinfo().isEmpty()) {
                        jedis.hset(uidkey + "rw", String.valueOf(vo.getRecordid()), ZipUtil.compress(vo.getReviewinfo()));
                    }
                    vo.setRecordinfo("");
                    vo.setReviewinfo("");
                    jedis.zadd(uidkey, vo.getPlaytime(), ZipUtil.compress(JSON.toJSONString(vo)));
                }
            }

            result.put("recordinfolist", recordInfoVOList);
            result.put("count", recordInfoVOList.size());
            if (recordInfoVOList.size() > 0) {
                if (recordInfoVOList.size() == rows + 1) {
                    result.put("nextPageStatus", true);
                } else {
                    result.put("nextPageStatus", false);
                }
            } else {
                result.put("nextPageStatus", false);
            }
            return result;
        } catch (Exception e) {
            LOG.error("getRecordInfoList>>>", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

        return result;
    }

    public List<UserInfoVO> getRoomInfoList(String uid, String recordid) {
        List<UserInfoVO> roomInfoList = new ArrayList<>();
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserRecordinfoKey_Only, uid);
            String json = jedis.hget(uidkey + "ri", recordid);
            if (json != null) {
                json = ZipUtil.uncompress(json);
                roomInfoList = JSONArray.parseArray(json, UserInfoVO.class);
            }

        } catch (Exception e) {
            LOG.error("getRecordInfoVo>>>", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return roomInfoList;
    }

    public List<ReviewVo> getRoomReviewList(String uid, String recordid) {
        List<ReviewVo> reviewVoList = new ArrayList<>();
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserRecordinfoKey_Only, uid);
            String json = jedis.hget(uidkey + "rw", recordid);
            if (json != null && !json.isEmpty()) {
                json = ZipUtil.uncompress(json);
                reviewVoList = JSONArray.parseArray(json, ReviewVo.class);
            }

        } catch (Exception e) {
            LOG.error("getRoomReviewList>>>", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return reviewVoList;
    }


    public HomeVO getHomeVO(String uid) {
        Jedis jedis = null;
        HomeVO homeVO = new HomeVO();
        try {
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.HomeKey, uid);
            String userInfo = jedis.get(uidkey);
            homeVO = JSON.parseObject(userInfo, HomeVO.class);
            if (homeVO == null) {
                CenterVO centerVO = UserService.getInstance().getUserCenter(uid);
                RecordVO recordVO = UserService.getInstance().getUserRecord(uid);
                CurrencyVO currencyVO = UserService.getInstance().getUserCurrency(uid);
                homeVO = new HomeVO();
                if (centerVO != null) {
                    homeVO.setNickname(centerVO.getNickname());
                    homeVO.setHeadpic(centerVO.getHeadpic());
                    homeVO.setHeadpicthumb(centerVO.getHeadpicthumb());
                    homeVO.setExp(centerVO.getExp());
                    homeVO.setLv(centerVO.getLv());

                    homeVO.setMaxexp(centerVO.getMaxexp());
                    homeVO.setCity(centerVO.getCity());
                    homeVO.setFollowsTotal(centerVO.getFollowsTotal());
                    homeVO.setFansTotal(centerVO.getFansTotal());
                    homeVO.setSeat(centerVO.getSeat());
                    homeVO.setSignature(centerVO.getSignature());
                    homeVO.setCharmNum(centerVO.getCharmNum());
                }


                if (recordVO != null) {
                    homeVO.setIntegral(recordVO.getIntegral());
                    homeVO.setVictorynum(recordVO.getVictorynum());
                    homeVO.setDefeatnum(recordVO.getDefeatnum());

                    homeVO.setTitle(TitleConfigLib.getTitle(recordVO.getIntegral()));
                }
                if (currencyVO != null) {
                    homeVO.setGoldnum(currencyVO.getGoldnum());
                    homeVO.setDiamond(currencyVO.getDiamondnum());
                    homeVO.setBinddiamond(currencyVO.getBinddiamondnum());
                }
                jedis.setex(uidkey, RECORD_INFO_SAVE, JSON.toJSONString(homeVO));
            }
            return homeVO;
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public void saveReview(String uid, List<ReviewVo> reviewVoList) {
        Jedis jedis = null;
        try {
            //更新缓存
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserReviewKey, uid);
            jedis.setex(uidkey, 600, JSON.toJSONString(reviewVoList));
        } catch (Exception e) {
            LOG.error("saveReview" + e.toString() + " uid:" + uid);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public void saveReviewByRoomId(String roomid, List<ReviewVo> reviewVoList) {
        Jedis jedis = null;
        try {
            //更新缓存
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserReviewKey, roomid);
            jedis.setex(uidkey, 600, JSON.toJSONString(reviewVoList));
        } catch (Exception e) {
            LOG.error("saveReviewByRoomId" + e.toString() + " uid:" + roomid);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }


    public List<ReviewVo> getTempReviw(String uid) {
        Jedis jedis = null;
        List<ReviewVo> reviewVoList = null;
        try {
            //更新缓存
            jedis = jedisNoShardPool.getResource();
            String uidkey = String.format(RedisKeyPrefix.UserReviewKey, uid);
            String reviewjson = jedis.get(uidkey);
            reviewVoList = JSON.parseArray(reviewjson, ReviewVo.class);
        } catch (Exception e) {
            LOG.error("saveReview" + e.toString() + " uid:" + uid);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return reviewVoList;
    }

    /**
     * 添加战绩
     *
     * @param recordInfoVO
     */
    public RecordInfoVO saveUserGameRecord(String uid, RecordInfoVO recordInfoVO, UserInfoModel userInfoModelRoom, RoomInterface.RoomSubType roomSubType) {
        //System.out.println("saveUserGameRecord>>>>>>>"+uid);
        if (uid != null) {
            Jedis jedis = null;
            try {
                UserInfoModel userInfoModel = getUserInfo(uid);
                if (recordInfoVO != null && userInfoModel != null) {
                    jedis = jedisNoShardPool.getResource();
                    String uidkey = String.format(RedisKeyPrefix.UserRecordinfoKey, uid);
                    String uidkeyonly = String.format(RedisKeyPrefix.UserRecordinfoKey_Only, uid);
                    String uidkeyonlycontest = String.format(RedisKeyPrefix.UserRecordinfoKey_Only_Contest, uid);

                    int wintimes = recordInfoVO.getWintimes();
                    if (wintimes == -1) {
                        recordInfoVO.setWintimes(0);
                        userInfoModel.setVictornum(0);
                    } else if (wintimes == 1) {
                        //查询最后一次战绩
                        LinkedHashSet<String> recordInfoVoSet = (LinkedHashSet<String>) jedis.zrevrange(uidkey, 0, 0);
                        for (String jo : recordInfoVoSet) {
                            String json = ZipUtil.uncompress(jo);
                            RecordInfoVO vo = JSON.parseObject(json, RecordInfoVO.class);
                            wintimes = vo.getWintimes() + 1;
                            recordInfoVO.setWintimes(wintimes);
                            userInfoModel.setVictornum(wintimes);
                            break;
                        }
                    }
                    //连胜有经验加成  玩家连胜三局以上时，玩家获得120%经验值，玩家连胜5局以上时，玩家获得150%经验值，玩家连胜十局以上时，获得180%经验值
                    int exp = recordInfoVO.getExp();
                    if (roomSubType == RoomInterface.RoomSubType.RoomType_Newbie_Normal) {
                        exp = (int) (exp * 1.5);
                    } else if (roomSubType == RoomInterface.RoomSubType.RoomType_Newbie_Advanced) {
                        exp = (int) (exp * 2.0);
                    }
                    //20170626 英宣 皇冠5级以上 经验加成20%
                    if (userInfoModel.getCrownlv() >= 5) {
                        exp = (int) (exp * 1.2);
                    }
                    if (wintimes >= 10) {
                        recordInfoVO.setExp((int) (exp * 1.8));
                    } else if (wintimes >= 5) {
                        recordInfoVO.setExp((int) (exp * 1.5));
                    } else if (wintimes >= 3) {
                        recordInfoVO.setExp((int) (exp * 1.2));
                    } else {
                        recordInfoVO.setExp(exp);
                    }

                    int recordid = UserDao.getInstance().addUserGameRecordInfo(uid, recordInfoVO);
                    String recordinfojson = JSON.toJSONString(recordInfoVO);
                    //更新缓存
                    jedis.zadd(uidkey, recordInfoVO.getPlaytime(), ZipUtil.compress(recordinfojson));
                    recordInfoVO.setRecordid(recordid);
                    jedis.zadd(uidkeyonly, recordInfoVO.getPlaytime(), ZipUtil.compress(recordinfojson));
                    try {
                        String contestvkey = String.format(RedisKeyPrefix.UserContestVictorKey, uid);
                        String userInfo = jedis.get(uidkey);
                        ContestVictorVO constestVictorVO = JSON.parseObject(userInfo, ContestVictorVO.class);
                        if (constestVictorVO == null){
                            constestVictorVO = new ContestVictorVO();
                        }
                        constestVictorVO.setLastgameisvictor(recordInfoVO.getOutcome());
                        constestVictorVO.setLastgametime(System.currentTimeMillis());
                        if (wintimes == -1){
                            wintimes = 0;
                        }
                        constestVictorVO.setSerialvictor(wintimes);
                        if (constestVictorVO.getMaxserialvictor() < constestVictorVO.getSerialvictor()){
                            constestVictorVO.setMaxserialvictor(constestVictorVO.getSerialvictor());
                        }
                        if (wintimes >0){//胜利了
                            constestVictorVO.setVictorynum(constestVictorVO.getVictorynum()+1);
                        }else {
                            constestVictorVO.setDefeatnum(constestVictorVO.getDefeatnum()+1);
                        }


                        jedis.setex(contestvkey, USER_INFO_SAVE_Contest, JSON.toJSONString(constestVictorVO));
                    }catch (Exception e){
                        LOG.error("saveUserGameRecord>>ContestVictorVO" ,e);
                    }
                    jedis.zadd(uidkeyonly, recordInfoVO.getPlaytime(), ZipUtil.compress(JSON.toJSONString(recordInfoVO)));
                    if (roomSubType != RoomInterface.RoomSubType.RoomType_Ladder){
                        userInfoModel.setNoplay_wolf(userInfoModelRoom.getNoplay_wolf());
                        userInfoModel.setNoplay_magic(userInfoModelRoom.getNoplay_magic());
                        userInfoModel.setNoplay_gurad(userInfoModelRoom.getNoplay_gurad());
                        userInfoModel.setNoplay_hunter(userInfoModelRoom.getNoplay_hunter());
                        userInfoModel.setNoplay_prophet(userInfoModelRoom.getNoplay_prophet());
                    }else {
                        jedis.zadd(uidkeyonlycontest, recordInfoVO.getPlaytime(), ZipUtil.compress(JSON.toJSONString(recordInfoVO)));
                        userInfoModel.setNoplay_wolf_contest(userInfoModelRoom.getNoplay_wolf());
                        userInfoModel.setNoplay_magic_contest(userInfoModelRoom.getNoplay_magic());
                        userInfoModel.setNoplay_gurad_contest(userInfoModelRoom.getNoplay_gurad());
                        userInfoModel.setNoplay_hunter_contest(userInfoModelRoom.getNoplay_hunter());
                        userInfoModel.setNoplay_prophet_contest(userInfoModelRoom.getNoplay_prophet());
                    }


                    String usermodelkey = String.format(RedisKeyPrefix.UserInfoKey, uid);
                    jedis.setex(usermodelkey, USER_INFO_SAVE, JSON.toJSONString(userInfoModel));
                    if (recordInfoVO.getOutcome() == 1) {
                        TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.FirstWin);
                        TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.FirstWin2);
                        TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.FirstWin3);
                        TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.FirstWin4);
                    }
                }
            } catch (Exception e) {
                LOG.error("saveUserGameRecord" ,e);
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }

        }
        return recordInfoVO;

    }

    public void updateUserGameInfo(String uid, RecordVO recordVO, long exp, RoomInterface.RoomMode mode,RoomInterface.RoomSubType roomSubType) {
        //System.out.println("updateUserGameInfo>>>intgral>>>>"+recordVO.getIntegral());
        //20170718 英宣修改需求，只有12人房间（只跟人数相关）加比赛场次和排行榜，其他只加经验和记录
        if (uid != null) {
            Jedis jedis = null;
            Jedis rankJedis = null;
            try {
                //System.out.println(System.currentTimeMillis()+">>>>>>>>"+JSON.toJSONString(recordVO)+">>>>"+exp+">>>>>"+uid);
                jedis = jedisNoShardPool.getResource();
                JedisPool rankJedisPool = RedisBucket.getInstance().getNoShardPool("rank");
                rankJedis = rankJedisPool.getResource();
                long R = recordVO.getIntegral();
                /*if (recordVO.getIntegral() < 1300) {
                    //扣分，保护
                    int minintegral = 1300;
                    long userintegral = recordVO.getIntegral();//用户现在的积分
                    if (userintegral<minintegral){
                        recordVO.setIntegral((long)minintegral);
                    }
                    *//*long addintegral = userintegral + recordVO.getIntegral();//用户算上这局之后的积分
                    //如果用户原来的积分大于50，并且，算完这局之后，积分比保护积分小
                    if (userintegral > minintegral && addintegral < minintegral) {
                        long setintegral = minintegral - userintegral;
                        recordVO.setIntegral(setintegral);
                    } else if (userintegral < minintegral) {
                        if (addintegral < 0) {
                            recordVO.setIntegral((long) 0);
                        } else {
                            recordVO.setIntegral(addintegral);
                        }

                    }*//*
                }*/
                CenterVO centerVO = UserService.getInstance().getUserCenter(uid);
                if (centerVO != null) {
                    //更新用户等级与经验
                    final long finalexp = exp + centerVO.getExp();
                    int lv = LevelConfigLib.getSuitableLevel(finalexp);
                    String[] arraystr = {"exp", "lv"};
                    UserDao.getInstance().updateUserInfo(uid, arraystr, finalexp, lv);
                    String centeruidkey = String.format(RedisKeyPrefix.UserCenterKey, uid);
                    centerVO.setLv(lv);
                    centerVO.setExp(finalexp);
                    long maxexp = LevelConfigLib.getSuitableMaxexp(finalexp);
                    centerVO.setMaxexp(maxexp);
                    centerVO.setIntegral((int)R);
                    jedis.setex(centeruidkey, USER_INFO_SAVE, JSON.toJSONString(centerVO));
                    String userinfokey = String.format(RedisKeyPrefix.UserInfoKey, uid);
                    String userinfokeystr = jedis.get(userinfokey);
                    UserInfoModel userInfoModel = JSON.parseObject(userinfokeystr, UserInfoModel.class);
                    if (userInfoModel != null) {
                        userInfoModel.setExp(finalexp);
                        userInfoModel.setLevel(lv);
                        userInfoModel.setIntegral((int)R);
                        jedis.setex(userinfokey, USER_INFO_SAVE, JSON.toJSONString(userInfoModel));
                    }

                    rankJedis.zadd(RedisKeyPrefix.RankExpKey, centerVO.getExp(), uid);


                }
                HomeVO homeVO = new HomeVO();
                //判断只有12人房
                if (mode == RoomInterface.RoomMode.RoomMode_Twelve){
                    UserDao.getInstance().updateUserGameInfo(uid, recordVO);//更新战绩场次
                    if (recordVO.getWolfvictorynum() == 1) {
                        rankJedis.zincrby(RedisKeyPrefix.RankWolfKey + ":day:" + Dateutils.getlogindate(), recordVO.getWolfvictorynum(), uid);
                        rankJedis.zincrby(RedisKeyPrefix.RankWolfKey + ":weak:" + Dateutils.getDateWeaks(), recordVO.getWolfvictorynum(), uid);
                        rankJedis.zincrby(RedisKeyPrefix.RankWolfKey + ":month:" + Dateutils.getMonthDate(), recordVO.getWolfvictorynum(), uid);
                    }
                    if (recordVO.getMagicvictorynum() == 1 ||
                            recordVO.getGuardnumvictorynum() == 1 ||
                            recordVO.getProphetvictorynum() == 1 ||
                            recordVO.getHuntervictorynum() == 1) {
                        rankJedis.zincrby(RedisKeyPrefix.RankGoodKey + ":day:" + Dateutils.getlogindate(), 1, uid);
                        rankJedis.zincrby(RedisKeyPrefix.RankGoodKey + ":weak:" + Dateutils.getDateWeaks(), 1, uid);
                        rankJedis.zincrby(RedisKeyPrefix.RankGoodKey + ":month:" + Dateutils.getMonthDate(), 1, uid);
                    }

                    if (roomSubType != RoomInterface.RoomSubType.RoomType_Ladder){
                        recordVO = UserDao.getInstance().getUserRecord(uid);
                    }else {
                        recordVO = getUserRecordContest(uid);
                    }
                    if (recordVO == null){
                        recordVO = new RecordVO();
                    }
                    //set redis
                    //if (recordVO != null) {
                        String centerkey = String.format(RedisKeyPrefix.UserRecordKey, uid);
                        if (roomSubType == RoomInterface.RoomSubType.RoomType_Ladder){
                            centerkey = String.format(RedisKeyPrefix.UserRecordContestKey, uid);
                        }
                        recordVO.setIntegral(R);
                        jedis.setex(centerkey, USER_INFO_SAVE, JSON.toJSONString(recordVO));

                        //统计数据缓存
                        if (roomSubType == RoomInterface.RoomSubType.RoomType_Ladder){
                            //天梯排行榜
                            String integeralkey = RedisKeyPrefix.RankIntegralKey;
                            //当前赛季，当前段位
                            List<ContestConfig> configList = ContestConfigLib.getinstance().getConfigList();
                            if(configList!=null && configList.size()>0){
                                ContestConfig contestConfig = configList.get(0);
                                List<Integer> gradeList = ContestService.gradeList;
                                int integral = (int) R;
                                int inthegradenum = 1300;
                                for (Integer ing : gradeList){
                                    if (ing < integral){
                                        inthegradenum = ing;
                                    }
                                }
                                int grdeindex = gradeList.indexOf(inthegradenum);
                                if (grdeindex!=0){
                                    //青铜的,不会更低了
                                    rankJedis.zrem(integeralkey + contestConfig.getId()+gradeList.get(grdeindex-1),uid);
                                }
                                if ((grdeindex+1) < gradeList.size()){
                                    rankJedis.zrem(integeralkey + contestConfig.getId()+gradeList.get(grdeindex+1),uid);
                                }

                                integeralkey = integeralkey + contestConfig.getId()+inthegradenum;

                            }
                            rankJedis.zadd(integeralkey, R , uid);
                        }
                        //rankJedis.zadd(RedisKeyPrefix.RankIntegralKey, recordVO.getIntegral(), uid);
                        rankJedis.zadd(RedisKeyPrefix.RankWolfKey, recordVO.getWolfvictorynum(), uid);
                        rankJedis.zadd(RedisKeyPrefix.RankGoodKey, recordVO.getCivyvictorynum() +
                                recordVO.getMagicvictorynum() +
                                recordVO.getGuardnumvictorynum() +
                                recordVO.getProphetvictorynum() +
                                recordVO.getHuntervictorynum(), uid);
                        /**
                         * 各个角色的排行榜，暂时注释，等待开启
                         */
                        /**
                        rankJedis.zadd(RedisKeyPrefix.RankCivyKey, recordVO.getCivyvictorynum(), uid);
                        rankJedis.zadd(RedisKeyPrefix.RankMagicKey, recordVO.getMagicvictorynum(), uid);
                        rankJedis.zadd(RedisKeyPrefix.RankProphetKey, recordVO.getProphetvictorynum(), uid);
                        rankJedis.zadd(RedisKeyPrefix.RankGuardKey, recordVO.getGuardnumvictorynum(), uid);
                        rankJedis.zadd(RedisKeyPrefix.RankHunterKey, recordVO.getHuntervictorynum(), uid);
                        **/

                        String rankvokey = String.format(RedisKeyPrefix.RankVOKey, uid);
                        RankVO rankVO = new RankVO();

                        homeVO.setIntegral(R);
                        homeVO.setVictorynum(recordVO.getVictorynum());
                        homeVO.setDefeatnum(recordVO.getDefeatnum());
                        homeVO.setTitle(TitleConfigLib.getTitle(R));


                        if (centerVO != null) {
                            //更新用户等级与经验
                            final long finalexp = exp + centerVO.getExp();


                            rankVO.setGender(centerVO.getGender());
                            rankVO.setNickname(centerVO.getNickname());
                            rankVO.setHeadpic(centerVO.getHeadpic());
                            rankVO.setHeadpicthumb(centerVO.getHeadpicthumb());
                            rankVO.setExp(finalexp);
                            rankVO.setLv(centerVO.getLv());
                            rankVO.setMaxexp(centerVO.getMaxexp());
                            rankVO.setCity(centerVO.getCity());
                            rankVO.setCharmNum(centerVO.getCharmNum());
                            rankJedis.zadd(RedisKeyPrefix.RankExpKey, centerVO.getExp(), uid);


                        }
                        rankVO.setIntegral(R);
                        rankVO.setVictorynum(recordVO.getVictorynum());
                        rankVO.setDefeatnum(recordVO.getDefeatnum());

                        rankVO.setWolfvictorynum(recordVO.getWolfvictorynum());
                        rankVO.setWolfdefeatnum(recordVO.getWolfdefeatnum());

                        rankVO.setGoodvictorynum(recordVO.getCivyvictorynum() + recordVO.getMagicvictorynum() + recordVO.getGuardnumvictorynum() + recordVO.getProphetvictorynum() + recordVO.getHuntervictorynum());
                        rankVO.setGooddefeatnum(recordVO.getCivydefeatnum() + recordVO.getMagicdefeatnum() + recordVO.getGuardnumdefeatnum() + recordVO.getProphetdefeatnum() + recordVO.getHunterdefeatnum());

                        rankVO.setCivyvictorynum(recordVO.getCivyvictorynum());
                        rankVO.setCivydefeatnum(recordVO.getCivydefeatnum());

                        rankVO.setMagicvictorynum(recordVO.getMagicvictorynum());
                        rankVO.setMagicdefeatnum(recordVO.getMagicdefeatnum());

                        rankVO.setGuardvictorynum(recordVO.getGuardnumvictorynum());
                        rankVO.setGuarddefeatnum(recordVO.getGuardnumdefeatnum());

                        rankVO.setProphetvictorynum(recordVO.getProphetvictorynum());
                        rankVO.setProphetdefeatnum(recordVO.getProphetdefeatnum());

                        rankVO.setHuntervictorynum(recordVO.getHuntervictorynum());
                        rankVO.setHunterdefeatnum(recordVO.getHunterdefeatnum());

                        rankVO.setUid(uid);
                        rankJedis.set(rankvokey, JSON.toJSONString(rankVO));



                    }
                //}
                if (centerVO != null){
                    homeVO.setNickname(centerVO.getNickname());
                    homeVO.setHeadpic(centerVO.getHeadpic());
                    homeVO.setHeadpicthumb(centerVO.getHeadpicthumb());
                    homeVO.setExp(centerVO.getExp());
                    homeVO.setLv(centerVO.getLv());
                    homeVO.setMaxexp(centerVO.getMaxexp());
                    CurrencyVO currencyVO = UserService.getInstance().getUserCurrency(uid);
                    if (currencyVO != null) {
                        homeVO.setGoldnum(currencyVO.getGoldnum());
                        homeVO.setDiamond(currencyVO.getDiamondnum());
                    }
                    String homekey = String.format(RedisKeyPrefix.HomeKey, uid);
                    jedis.setex(homekey, RECORD_INFO_SAVE, JSON.toJSONString(homeVO));
                }


                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame);
                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame2);
                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame3);
                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame4);
                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame5);
                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame6);
                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame7);
                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.PlayGame8);
            } catch (Exception e) {
                LOG.error("updateUserGameInfo", e);
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
                if (rankJedis != null) {
                    rankJedis.close();
                }
            }


        }
    }

    public void addUserExp(String uid, Integer exp) {
        Jedis jedis = null;
        try {
            String uidkey = String.format(RedisKeyPrefix.UserCenterKey, uid);
            jedis = jedisNoShardPool.getResource();
            String userInfo = jedis.get(uidkey);
            CenterVO centerVO = JSON.parseObject(userInfo, CenterVO.class);
            if (centerVO == null) {
                centerVO = UserService.getInstance().getUserCenter(uid);
            }
            if (centerVO != null) {
                //更新用户等级与经验
                final long finalexp = exp + centerVO.getExp();
                int lv = LevelConfigLib.getSuitableLevel(finalexp);
                if (lv != centerVO.getLv()) {
                    String[] arraystr = {"exp", "lv"};
                    UserService.getInstance().updateUserInfo(uid, arraystr, finalexp, lv);
                }

            }
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }

    }

    public void updateUserInfo(final String uid, String[] filedNames, Object... filedValues) {
        try {
            UserDao.getInstance().updateUserInfo(uid, filedNames, filedValues);
            AsyncManager.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    instance.reloadUserToRedis(uid);
                    UserInfoModel userInfoModel = UserService.getInstance().getUserInfo(String.valueOf(uid));
                    UserSearchVo pair = new UserSearchVo(String.valueOf(uid), userInfoModel.getLuckId(), userInfoModel.getNickname(), userInfoModel.getGender(), userInfoModel.getHeadpicthumb(), userInfoModel.getLevel());
                    UserIndexer.getInstance().offerSaveQueue(pair, true);
                }
            });
        } catch (Exception e) {
            LOG.error("updateUserInfo" + e.toString() + " uid:" + uid, e);
        }

    }

    public void reloadUserToRedis(String fuid) {
        UserInfoModel userInfoModel = UserDao.getInstance().getUserInfo(fuid);
        //set redis
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            if (userInfoModel != null) {
                String uidkey = String.format(RedisKeyPrefix.UserInfoKey, fuid);
                jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(userInfoModel));
            }
            CenterVO centerVO = UserDao.getInstance().getUserCenter(fuid);
            //set redis
            if (centerVO != null) {
                String uidkey = String.format(RedisKeyPrefix.UserCenterKey, fuid);
                if (centerVO.getLv() == 0) {
                    int lv = LevelConfigLib.getSuitableLevel(centerVO.getExp());
                    centerVO.setLv(lv);
                }
                long maxexp = LevelConfigLib.getSuitableMaxexp(centerVO.getExp());
                centerVO.setMaxexp(maxexp);
                jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(centerVO));
            }

            RecordVO recordVO = UserService.getInstance().getUserRecord(fuid);
            CurrencyVO currencyVO = UserService.getInstance().getUserCurrency(fuid);
            HomeVO homeVO = new HomeVO();
            if (centerVO != null) {
                homeVO.setNickname(centerVO.getNickname());
                homeVO.setHeadpic(centerVO.getHeadpic());
                homeVO.setHeadpicthumb(centerVO.getHeadpicthumb());
                homeVO.setExp(centerVO.getExp());
                homeVO.setLv(centerVO.getLv());
                homeVO.setMaxexp(centerVO.getMaxexp());
                homeVO.setCity(centerVO.getCity());
                homeVO.setCharmNum(centerVO.getCharmNum());
            }
            if (recordVO != null) {
                homeVO.setIntegral(recordVO.getIntegral());
                homeVO.setVictorynum(recordVO.getVictorynum());
                homeVO.setDefeatnum(recordVO.getDefeatnum());
                homeVO.setTitle(TitleConfigLib.getTitle(recordVO.getIntegral()));
            }
            if (currencyVO != null) {
                homeVO.setGoldnum(currencyVO.getGoldnum());
                homeVO.setDiamond(currencyVO.getDiamondnum());
            }
            String uidkey = String.format(RedisKeyPrefix.HomeKey, fuid);
            jedis.setex(uidkey, RECORD_INFO_SAVE, JSON.toJSONString(homeVO));
            Pair<String, RoomInterface.RoomSubType> lastLoginRoom = RoomService.getInstance().getLastLoginRoom(fuid);
            if (lastLoginRoom != null) {
                String roomId = lastLoginRoom.getLeft();
                if (roomId != null && !roomId.isEmpty()) {
                    ZkService.getInstance().rpcSomeCometServerByHttp(roomId, String.format("/room/change_room_user?clientId=%s", fuid));
                }
            }

        } catch (Exception e) {
            LOG.error("updateUserInfo>>>run>>>", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /**
     * 更新用户绑定房间
     *
     * @param uid
     * @param roomId
     */
    public void updateBindRoom(String uid, String roomId) {
        LOG.info(String.format("updateBindRoom>>roomId:{%s},uid:{%s}",roomId,uid));
        if (roomId.isEmpty()) {
            roomId = null;
        }
        try {
            String[] filedNames = {"bindRoom"};
            UserService.getInstance().updateUserInfo(uid, filedNames, roomId);
        } catch (Exception e) {
            LOG.error("updateUserInfo>>>run>>>", e);
        } finally {

        }
    }

    public UserAccountModel getUserAccountModelByUid(String uid) {
        return UserDao.getInstance().getUserAccountByuid(uid);
    }

    public Map<String, Object> joyLogin(String joyuid, final String nickname, final String headpic,
                                        final String loginMethod, final String mobileModel,
                                        final String channel, final String mobileVersion,
                                        final Integer os, String token, final String packageName) throws IOException {
        Map<String, Object> result = new HashMap<String, Object>(1);
        result.put("zego_appID", ZEGO_APPID);
        result.put("zego_signKey", ZEGO_SIGNKEY);
        try {
            //校验token和uid是否对应
            if (joyuid != null && token != null && !token.isEmpty()) {
                // 解密
                String decryptResult = AesUtil.decrypt(token, Joy_Uid_Key);
                if (decryptResult != null) {
                    String[] deuid = decryptResult.split(":");
                    if (deuid.length == 2) {
                        String tokenuid = deuid[0];
                        String times = deuid[1];
                        int invalidtime = 7 * 24 * 3600 * 1000;
                        if (joyuid.equals(tokenuid) && System.currentTimeMillis() - Long.valueOf(times) <= invalidtime) {
                            //正确的token
                            Jedis jedis = null;
                            UserAccountModel accountModel = null;
                            try {
                                jedis = jedisNoShardPool.getResource();
                                String uidkey = String.format(RedisKeyPrefix.UserInfoJoyuidKey, joyuid);
                                String userInfo = jedis.get(uidkey);
                                accountModel = JSON.parseObject(userInfo, UserAccountModel.class);
                                if (accountModel == null) {
                                    accountModel = UserDao.getInstance().getUserAccountByJoyuid(joyuid);
                                    //set redis
                                    if (accountModel != null)
                                        jedis.setex(uidkey, USER_INFO_SAVE, JSON.toJSONString(accountModel));
                                }
                                //201705017金秀提出修改 用户每次登录保存最后一次登录信息
                                if (accountModel != null) {
                                    String loginuidkey = String.format(RedisKeyPrefix.UserInfoJoyuidKey, accountModel.getUid());
                                    jedis.setex(loginuidkey, USER_INFO_SAVE, mobileVersion + "_" + os);
                                }
                            } finally {
                                if (jedis != null) {
                                    jedis.close();
                                }
                            }
                            int isfirstlogin = 0;//0不是第一次登录，1第一次登录
                            //自动注册 账户数据以及用户数据
                            if (accountModel == null) {
                                if (loginMethod.equals("mobile")) {
                                    isfirstlogin = 1;
                                }
                                try {
                                    accountModel = UserService.getInstance().insertModel(null,
                                            null, nickname, headpic, joyuid);
                                    final int uid = accountModel.getUid();
                                    AsyncManager.getInstance().execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            Jedis jedis = null;
                                            try {
                                                jedis = jedisNoShardPool.getResource();
                                                String loginuidkey = String.format(RedisKeyPrefix.UserInfoJoyuidKey, uid);
                                                jedis.setex(loginuidkey, USER_INFO_SAVE, mobileVersion + "_" + os);
                                                String uidkey = String.format(RedisKeyPrefix.UserLoginKey, uid + "_"
                                                        + Dateutils.getlogindate());
                                                jedis.setex(uidkey, 24 * 3600, String.valueOf(uid));
                                                UserLoginDao.getInstance().addUserLogin(uid,
                                                        loginMethod, mobileModel, channel, mobileVersion, os, 1, packageName);
                                                ItemService.getInstance().addUserItem(String.valueOf(uid), ConstantDefine.GoodsConfig.normalRoomCardId, 10, 0);
                                                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.LoginGame);
                                            } catch (Exception e) {
                                                LOG.error("joyLogin>>>Runnable", e);
                                            } finally {
                                                if (jedis != null) {
                                                    jedis.close();
                                                }
                                            }

                                        }
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                            } else {
                                final int uid = accountModel.getUid();

                                AsyncManager.getInstance().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        Jedis jedis = null;
                                        try {
                                            //记录登录
                                            //今天是否记录过
                                            jedis = jedisNoShardPool.getResource();
                                            String uidkey = String.format(RedisKeyPrefix.UserLoginKey, uid + "_"
                                                    + Dateutils.getlogindate());
                                            String ul = jedis.get(uidkey);
                                            if (ul == null) {//没有记录过才保存
                                                jedis.setex(uidkey, 24 * 3600, String.valueOf(uid));
                                                UserLoginDao.getInstance().addUserLogin(uid,
                                                        loginMethod, mobileModel, channel, mobileVersion, os, 2, packageName);
                                                ItemService.getInstance().addUserItem(String.valueOf(uid), ConstantDefine.GoodsConfig.normalRoomCardId, 10, 0);
                                                TaskService.getInstance().finishTask(String.valueOf(uid), TaskConfigLib.LoginGame);
                                                /*//TODO等待删除
												UserInfoModel userInfoModel = UserService.getInstance().getUserInfo(String.valueOf(uid));
												UserSearchVo pair = new UserSearchVo(String.valueOf(uid),userInfoModel.getNickname(),userInfoModel.getGender(),userInfoModel.getHeadpicthumb(),userInfoModel.getLevel());
												UserIndexer.getInstance().offerSaveQueue(pair,true);*/
                                            }
                                        } catch (Exception e) {
                                            LOG.error("joyLogin>>>Runnable", e);
                                        } finally {
                                            if (jedis != null) {
                                                jedis.close();
                                            }
                                        }

                                    }
                                });

                            }
                            if (accountModel != null){
                                int state = accountModel.getStatus();
                                if (state == 0){
                                    result.put("code", CodeContant.clientNotFount);
                                    result.put("reason", "帐号已被暂时封停,QQ群626693185");
                                    result.put("ok", Boolean.FALSE);
                                    return result;
                                }
                                result.put("uid", accountModel.getUid());
                                result.put("accountid", accountModel.getAccountid());
                                result.put("client_id", accountModel.getUid());
                                result.put("isfirstlogin", isfirstlogin);

                                UserInfoModel userInfo = UserService.getInstance().getUserInfo(accountModel.getUid().toString());
                                if (userInfo != null) {
                                    result.put("nickname", userInfo.getNickname());
                                    result.put("headpic", userInfo.getHeadpic());
                                    result.put("lv", userInfo.getLevel());
                                    //result.put("userInfo", userInfo);
                                }

                                String matchtoken = TokenId.nextId() + "#" + accountModel.getUid() + "#" + System.currentTimeMillis();
                                byte[] matchsi = wolfman.services.EncryptService.getInstance().
                                        encryptByDefaultDes(matchtoken);
                                String matchhex = wolfman.services.EncryptService.getInstance().toHex(matchsi);
                                result.put("matchToken", matchhex);

                                String mytoken = TokenId.nextId() + "#" + accountModel.getUid() + "#"
                                        + System.currentTimeMillis();
                                byte[] si = wolfman.services.EncryptService.getInstance().
                                        encryptByDefaultDes(mytoken);
                                String hex = wolfman.services.EncryptService.getInstance().toHex(si);
                                result.put("token", hex);
                            }
                        } else {
                            result.put("code", CodeContant.loginTokenInvalid);
                            result.put("reason", "token过期");
                            result.put("ok", Boolean.FALSE);
                            return result;
                        }
                    } else {
                        result.put("code", CodeContant.loginTokenInvalid);
                        result.put("reason", "token格式错误");
                        result.put("ok", Boolean.FALSE);
                        return result;
                    }

                } else {
                    result.put("code", CodeContant.loginTokenInvalid);
                    result.put("reason", "token解析错误");
                    result.put("ok", Boolean.FALSE);
                    return result;
                }

            } else {
                result.put("code", CodeContant.loginTokenInvalid);
                result.put("reason", "明日世界token失效");
                result.put("ok", Boolean.FALSE);
                return result;
            }

        } catch (Exception e) {
            e.printStackTrace();
            result.put("ok", Boolean.FALSE);
        }
        return result;
    }

    private final static String fish_source = "nextjoylrs";
    private final static long fish_appid = 1205786149;

    public void checkSmallFish(String idfa) {
        if (idfa != null && !idfa.isEmpty()) {
            ShardedJedisPool jedisPool = RedisBucket.getInstance().getShardPool("other");
            ShardedJedis jedis = null;
            try {
                if (jedisPool != null) {
                    jedis = jedisPool.getResource();
                    if (jedis != null) {
                        String isrepeat = jedis.hget("fish:" + fish_source + fish_appid, idfa);
                        if (isrepeat != null && !isrepeat.isEmpty()) {
                            List<String> resultlist = JSONArray.parseArray(isrepeat, String.class);
                            if (resultlist != null && resultlist.size() > 1) {
                                String callback = resultlist.get(1);
                                try {
                                    Unirest.get(callback).asJson();
                                } catch (UnirestException e) {
                                    LOG.error("checkSmallFish>>", e);
                                }
                            }

                        }

                    }
                }
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }
    }

    public String getUserLoginVersion(String uid) {
        String verson = "";
        Jedis jedis = null;
        try {
            jedis = jedisNoShardPool.getResource();
            String loginuidkey = String.format(RedisKeyPrefix.UserInfoJoyuidKey, uid);
            verson = jedis.get(loginuidkey);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return verson;
    }

    /***
     * 关联用户推送账号
     * @param uid
     * @return
     */
    public void bindPushGexin(String uid, String cid, String appId) {
        ShardedJedisPool noShardPool = RedisBucket.getInstance().getShardPool(RedisNameOther);
        ShardedJedis jedis = null;
        try {
            jedis = noShardPool.getResource();
            String push_info = cid + "@" + appId;
            jedis.hset(PushGexin, uid, push_info);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    /***
     * 获取用户信息 用于关注或者粉丝列表
     * @param follows
     */
    public List<UserInfoModel> getUserInfoForFollow(Set<String> follows) {
        List<UserInfoModel> list = new ArrayList<>();
        if (follows != null && follows.size() > 0) {
            Jedis jedis = null;
            try {
                jedis = jedisNoShardPool.getResource();
                List<String> rediskey = new ArrayList<>();
                for (String uid : follows) {
                    rediskey.add(String.format(RedisKeyPrefix.UserInfoKey, uid));
                }
                try {
                    List<String> userinfostrlist = jedis.mget(rediskey.toArray(new String[rediskey.size()]));
                    List<String> haveselectlist = new ArrayList<>();
                    for (String json : userinfostrlist) {
                        if (json != null) {
                            UserInfoModel userInfoModel = JSON.parseObject(json, UserInfoModel.class);
                            if (userInfoModel != null) {
                                int lv = LevelConfigLib.getSuitableLevel(userInfoModel.getExp());
                                userInfoModel.setLevel(lv);
                                list.add(userInfoModel);
                                haveselectlist.add(userInfoModel.getUid());
                            }
                        }
                    }

                    if (list.size() != follows.size()) {
                        List<String> noselectlist = new ArrayList<>();
                        for (String str : follows) {
                            if (!haveselectlist.contains(str)) {
                                noselectlist.add(str);
                                UserInfoModel userInfoModel = UserService.getInstance().getUserInfo(str);
                                if (userInfoModel != null) {
                                    list.add(userInfoModel);
                                }
                            }
                        }
						/*if (noselectlist.size()==1){
							UserInfoModel userInfoModel = UserService.getInstance().getUserInfo(noselectlist.get(0));
							if (userInfoModel != null){
								list.add(userInfoModel);
							}
						}else {
							String[] ids = noselectlist.toArray(new String[noselectlist.size()]);
							List<UserInfoModel> manyUserinfoList = UserService.getInstance().getManyUserInfoModel(ids);
						}*/
                    }

                    return list;
                } catch (Exception e) {
                    LOG.error("getUserInfoForFollow>>", e);
                }
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }

        return list;
    }


    public void addUserSuggestion(String uid, String troublelist, String suggestion, String contact,
                                  Integer os, String pic1, String pic2, String pic3, String pic4) {
        if (uid != null) {
            UserDao.getInstance().addUserSuggestion(uid, troublelist, suggestion, contact, os, pic1, pic2, pic3, pic4);
        }
    }

    /**
     * 查询靓号的绑定关系
     *
     * @param luckId
     * @return
     */
    public String viewLuckId(String luckId) {
        if (luckId != null) {
            Jedis jedis = null;
            try {
                jedis = jedisNoShardPool.getResource();
                return jedis.hget(RedisKeyPrefix.UserLuckIdKey, luckId);
            } catch (Exception e) {
                LOG.error("getUserInfo>>", e);
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }
        return null;
    }

    /***
     * 绑定靓号
     * @param uid
     * @param luckId
     */
    public long bindLuckId(String uid, String luckId) {
        if (luckId != null) {
            Jedis jedis = null;
            try {
                jedis = jedisNoShardPool.getResource();
                return jedis.hset(RedisKeyPrefix.UserLuckIdKey, luckId, uid);
            } catch (Exception e) {
                LOG.error("bindLuckId>>", e);
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }
        return 0;
    }

    public long unbindLuckId(String luckId) {
        if (luckId != null) {
            Jedis jedis = null;
            try {
                jedis = jedisNoShardPool.getResource();
                return jedis.hdel(RedisKeyPrefix.UserLuckIdKey, luckId);
            } catch (Exception e) {
                LOG.error("bindLuckId>>", e);
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }
        return 0;
    }

    /**
     * 用户绑定一个高级房号
     *
     * @param clientId
     * @param roomId
     */
    public void bindAdvancedId(String clientId, String roomId) {
        if (!CheckUtils.hasInValidArgumentsEveryOf(clientId)) {
            Jedis jedis = null;
            try {
                String[] arraystr = {"bindRoom"};
                UserService.getInstance().updateUserInfo(clientId, arraystr, roomId);
            } catch (Exception e) {
                LOG.error("bindAdvancedId>>", e);
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }
    }

    /**
     * 用户绑定一个高级房号
     *
     * @param clientId
     */
    public void unBindAdvancedId(String clientId) {
        bindAdvancedId(clientId,null);
    }

    /**
     * 用户绑定一个靓房间号
     * @param clientId
     * @param bindRoomVo
     */
    public void bindLuckRoomId(String clientId, BindRoomVo bindRoomVo) {
        Jedis jedis = null;
        try {
            String redisKey = RoomConfigLib.redisBucketLuckyUser2RoomPrefix();
            String bindRoomString = JSON.toJSONString(bindRoomVo);
            jedis.hset(redisKey, clientId, bindRoomString);
        } catch (Exception e) {
            LOG.error("bindLuckRoomId>>", e);
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
