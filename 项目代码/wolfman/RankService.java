package wolfman.services;

import com.alibaba.fastjson.JSON;
import org.apache.log4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Tuple;
import wolfman.core.cache.redis.RedisBucket;
import wolfman.core.cache.redis.RedisKeyPrefix;
import wolfman.core.db.domain.impl.UserModel;
import wolfman.dictionary.ContestConfig;
import wolfman.dictionary.lib.ContestConfigLib;
import wolfman.room.state.Dateutils;
import wolfman.web.dao.domain.RankVO;
import wolfman.web.db.domain.UserInfoModel;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by wsc on 2017/2/20.
 */
public class RankService extends BaseService{

    private final static Logger LOG = Logger
            .getLogger(RankService.class);
    private static class RankServiceHolder {
        private static wolfman.services.RankService cache = new wolfman.services.RankService();
    }
    private final static String RKEY = "rank";
    private static long selts = 0;
    private static AtomicLong syncTime = new AtomicLong(0);
    private final static int intervalts = 60*1000;
    private static List<RankVO> expRankVOList = new ArrayList<>();
    private static List<RankVO> wolfRankVOList = new ArrayList<>();
    private static List<RankVO> wolfRankDayVOList = new ArrayList<>();
    private static List<RankVO> wolfRankWeakVOList = new ArrayList<>();
    private static List<RankVO> wolfRankMonthVOList = new ArrayList<>();
    private static List<RankVO> goodRankVOList = new ArrayList<>();
    private static List<RankVO> goodRankDayVOList = new ArrayList<>();
    private static List<RankVO> goodRankWeakVOList = new ArrayList<>();
    private static List<RankVO> goodRankMonthVOList = new ArrayList<>();
    private static List<RankVO> civyRankVOList = new ArrayList<>();
    private static List<RankVO> magicRankVOList = new ArrayList<>();
    private static List<RankVO> guardRankVOList = new ArrayList<>();
    private static List<RankVO> prophetRankVOList = new ArrayList<>();
    private static List<RankVO> hunterRankVOList = new ArrayList<>();
    private static HashMap<String,Object> totalRankMap = new HashMap<>();

    public static RankService getInstance() {
        return RankServiceHolder.cache;
    }

    public List<RankVO> expRank(){
        List<RankVO> rankVOList = expRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try {
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankExpKey;
                rankVOList = getRankVOList(key,jedis);
                expRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("expRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }
        return rankVOList;
    }


    public HashMap integralRank(int cid,int itg,String clientid) {
        HashMap map = new HashMap();
        List<RankVO> rankVOList = new ArrayList<>();
        Jedis jedis =null;
        try{
            jedis = jedisNoShardPool.getResource();
            String key = RedisKeyPrefix.RankIntegralKey + cid + itg;
            //rankVOList = getRankVOList(key,jedis);
            if(jedis !=null){
                Set<Tuple> zrankWithScores = jedis.zrevrangeWithScores(key,0,98);
                if (zrankWithScores != null) {
                    List<String> keylist = new ArrayList<>();
                    Map<String,Double> scoresmap = new HashMap<>();
                    for (Tuple tuple : zrankWithScores) {
                        String uid = tuple.getElement();
                        if (uid!=null){
                            scoresmap.put(uid,tuple.getScore());
                            keylist.add(String.format(RedisKeyPrefix.RankVOKey,uid));
                        }
                    }
                    if (keylist.size()>0){
                        List<String> rankvojson = jedis.mget(keylist.toArray(new String[keylist.size()]));
                        for (String jsons : rankvojson){
                            if(jsons!=null){
                                RankVO vo = JSON.parseObject(jsons,RankVO.class);
                                vo.setIntegral(scoresmap.get(vo.getUid()).intValue());
                                rankVOList.add(vo);
                            }

                        }
                    }
                }
            }
            map.put("rankList",rankVOList);
            //查询用户排名
            if (clientid!=null){
                Integer uid = Integer.valueOf(clientid);
                Long rank = jedis.zrevrank(key,clientid);
                Double score = jedis.zscore(key,clientid);
                map.put("rank",rank==null?0:rank + 1);
                map.put("score",score==null?0:score);
            }

        }catch (Exception e){
            LOG.error("expRank>>>",e);
        }finally {
            if (jedis!=null){
                jedis.close();
            }
        }

        return map;
    }


    public List<RankVO> wolfRank() {

        List<RankVO> rankVOList = wolfRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try{
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankWolfKey;
                rankVOList = getRankVOList(key,jedis);
                wolfRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("expRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }

        return rankVOList;
    }
    private AtomicBoolean modifyLock = new AtomicBoolean(false);
    public List<RankVO> wolfRankTime(String time) {
        List<RankVO> rankVOList = new ArrayList<>();
        /*long currentTimeMillis  = syncTime.get();
        if(modifyLock.compareAndSet(false,true)){
            if (System.currentTimeMillis() - currentTimeMillis > intervalts){
                Jedis jedis =null;
                try{
                    jedis = jedisNoShardPool.getResource();

                    String datestr = "";
                    if (time.equals("day")){
                        datestr = Dateutils.getlogindate();
                    }else if (time.equals("weak")){
                        datestr = Dateutils.getDateWeaks();
                    }else if (time.equals("month")){
                        datestr = Dateutils.getMonthDate();
                    }
                    String key = RedisKeyPrefix.RankWolfKey + time + datestr;
                    LinkedHashSet<String> uidlist = (LinkedHashSet<String>) jedis.zrevrange(key,0,98);
                    if (uidlist!=null&& uidlist.size()>0){
                        List<String> keylist = new ArrayList<>();
                        for (String uid : uidlist){
                            keylist.add(String.format(RedisKeyPrefix.RankVOKey,uid));
                        }
                        List<String> rankvojson = jedis.mget(keylist.toArray(new String[1]));
                        for (String jsons : rankvojson){
                            if(jsons!=null){
                                RankVO vo = JSON.parseObject(jsons,RankVO.class);
                                if (vo!=null){
                                    vo.setVictorynum(vo.getWolfvictorynum());
                                    vo.setDefeatnum(vo.getWolfdefeatnum());
                                    rankVOList.add(vo);
                                }

                            }

                        }
                    }
                    if (time.equals("day")){
                        wolfRankDayVOList = rankVOList;
                    }else if (time.equals("weak")){
                        wolfRankWeakVOList = rankVOList;
                    }else if (time.equals("month")){
                        wolfRankMonthVOList = rankVOList;
                    }

                }catch (Exception e){
                    LOG.error("expRank>>>",e);
                }finally {
                    if (jedis!=null){
                        jedis.close();
                    }
                }
            }
            modifyLock.set(false);
            syncTime.set(System.currentTimeMillis());
        }


        if (time.equals("day")){
            return wolfRankDayVOList;
        }else if (time.equals("weak")){
            return  wolfRankWeakVOList;
        }else if (time.equals("month")){
            return  wolfRankMonthVOList;
        }*/
        Jedis jedis =null;
        try{
            jedis = jedisNoShardPool.getResource();

            String datestr = "";
            if (time.equals("day")){
                datestr = Dateutils.getlogindate();
            }else if (time.equals("weak")){
                datestr = Dateutils.getDateWeaks();
            }else if (time.equals("month")){
                datestr = Dateutils.getMonthDate();
            }
            String key = RedisKeyPrefix.RankWolfKey + ":" + time + ":" + datestr;
            rankVOList = getRankVOList(key,jedis);
        }catch (Exception e){
            LOG.error("wolfRankTime>>>",e);
        }finally {
            if (jedis!=null){
                jedis.close();
            }
        }
        return rankVOList;
    }

    public List<RankVO> goodRank() {
        List<RankVO> rankVOList = goodRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try {
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankGoodKey;
                rankVOList = getRankVOList(key,jedis);
                goodRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("goodRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }


        return rankVOList;
    }
    public List<RankVO> goodRankTime(String time) {
        List<RankVO> rankVOList = new ArrayList<>();
        /*if (System.currentTimeMillis() - selts > intervalts){
            String datestr = "";
            if (time.equals("day")){
                datestr = Dateutils.getlogindate();
            }else if (time.equals("weak")){
                datestr = Dateutils.getDateWeaks();
            }else if (time.equals("month")){
                datestr = Dateutils.getMonthDate();
            }
            Jedis jedis =null;
            try {
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankGoodKey + time + datestr;
                rankVOList = getRankVOList(key,jedis);
                if (time.equals("day")){
                    goodRankDayVOList = rankVOList;
                }else if (time.equals("weak")){
                    goodRankWeakVOList = rankVOList;
                }else if (time.equals("month")){
                    goodRankMonthVOList = rankVOList;
                }
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("goodRankTime>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }
        if (time.equals("day")){
            return goodRankDayVOList;
        }else if (time.equals("weak")){
            return goodRankWeakVOList;
        }else if (time.equals("month")){
            return goodRankMonthVOList;
        }*/


        Jedis jedis =null;
        try {
            jedis = jedisNoShardPool.getResource();
            String datestr = "";
            if (time.equals("day")){
                datestr = Dateutils.getlogindate();
            }else if (time.equals("weak")){
                datestr = Dateutils.getDateWeaks();
            }else if (time.equals("month")){
                datestr = Dateutils.getMonthDate();
            }
            String key = RedisKeyPrefix.RankGoodKey + ":" + time + ":" + datestr;
            rankVOList = getRankVOList(key,jedis);
        }catch (Exception e){
            LOG.error("goodRankTime>>>",e);
        }finally {
            if (jedis!=null){
                jedis.close();
            }
        }
        return rankVOList;
    }

    public List<RankVO> civyRank() {
        List<RankVO> rankVOList = civyRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try{
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankCivyKey;
                rankVOList = getRankVOList(key,jedis);
                civyRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("civyRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }

        return rankVOList;
    }

    public List<RankVO> magicRank() {
        List<RankVO> rankVOList = magicRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try{
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankMagicKey;
                rankVOList = getRankVOList(key,jedis);
                magicRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("magicRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }
        return rankVOList;
    }


    public List<RankVO> guradRank() {
        List<RankVO> rankVOList = guardRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try{
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankGuardKey;
                rankVOList = getRankVOList(key,jedis);
                guardRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("guradRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }
        return rankVOList;
    }

    public List<RankVO> prophetRank() {
        List<RankVO> rankVOList = prophetRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try{
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankProphetKey;
                rankVOList = getRankVOList(key,jedis);
                prophetRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("prophetRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }
        return rankVOList;
    }

    public List<RankVO> hunterRank() {
        List<RankVO> rankVOList = hunterRankVOList;
        if (rankVOList.size()<1 || System.currentTimeMillis() - selts > intervalts){
            rankVOList = new ArrayList<>();
            Jedis jedis =null;
            try{
                jedis = jedisNoShardPool.getResource();
                String key = RedisKeyPrefix.RankHunterKey;
                rankVOList = getRankVOList(key,jedis);
                hunterRankVOList = rankVOList;
                selts = System.currentTimeMillis();
            }catch (Exception e){
                LOG.error("hunterRank>>>",e);
            }finally {
                if (jedis!=null){
                    jedis.close();
                }
            }
        }
        return rankVOList;
    }

    public List<RankVO> getRankVOList(String key,Jedis jedis){
        List<RankVO> rankVOList = new ArrayList<>();
        if(jedis !=null){
            Set<Tuple> zrankWithScores = jedis.zrevrangeWithScores(key,0,98);
            if (zrankWithScores != null) {
                List<String> keylist = new ArrayList<>();
                Map<String,Double> scoresmap = new HashMap<>();
                for (Tuple tuple : zrankWithScores) {
                    String uid = tuple.getElement();
                    if (uid!=null){
                        scoresmap.put(uid,tuple.getScore());
                        keylist.add(String.format(RedisKeyPrefix.RankVOKey,uid));
                    }
                }
                if (keylist.size()>0){
                    List<String> rankvojson = jedis.mget(keylist.toArray(new String[keylist.size()]));
                    for (String jsons : rankvojson){
                        if(jsons!=null){
                            RankVO vo = JSON.parseObject(jsons,RankVO.class);
                            vo.setVictorynum(scoresmap.get(vo.getUid()).intValue());
                            //vo.setVictorynum(vo.getGoodvictorynum());
                            vo.setDefeatnum(vo.getDefeatnum());
                            rankVOList.add(vo);
                        }

                    }
                }
            }
        }

        return rankVOList;
    }

    public HashMap getUserRank(String uid){
        HashMap body = null;
        Jedis rankJedis = null;
        try {
            rankJedis = jedisNoShardPool.getResource();
            /*String uidkey = String.format(RedisKeyPrefix.UserRankKey,uid);
            String mapjson = rankJedis.get(uidkey);
            if (mapjson != null){
                body = JSON.parseObject(mapjson,HashMap.class);
            }
            if (body == null){
                rankJedis.setex(uidkey,intervalts,JSON.toJSONString(body));
            }*/
            body = new HashMap();
            Long rank ;
            Double score ;
            //经验
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankExpKey,uid);

            String vokey = String.format(RedisKeyPrefix.RankVOKey,uid);
            String json = rankJedis.get(vokey);
            RankVO vo = JSON.parseObject(json,RankVO.class);
            if(vo == null){
                vo = new RankVO();
            }
            //score = rankJedis.zscore(RedisKeyPrefix.RankExpKey,uid);
            body.put("exp_rank",rank==null?0:rank + 1);
            body.put("exp_score",vo.getExp());
            //狼人
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankWolfKey,uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankWolfKey,uid);
            body.put("wolf_rank",rank==null?0:rank + 1);
            body.put("wolf_score",score==null?0:score);
            //狼人-日
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankWolfKey+":day:"+Dateutils.getlogindate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankWolfKey+":day:"+Dateutils.getlogindate(),uid);
            body.put("wolf_day_rank",rank==null?0:rank + 1);
            body.put("wolf_day_score",score==null?0:score);
            //狼人-周
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankWolfKey+":weak:"+Dateutils.getDateWeaks(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankWolfKey+":weak:"+Dateutils.getDateWeaks(),uid);
            body.put("wolf_weak_rank",rank==null?0:rank + 1);
            body.put("wolf_weak_score",score==null?0:score);
            //狼人-月
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankWolfKey+":month:"+Dateutils.getMonthDate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankWolfKey+":month:"+Dateutils.getMonthDate(),uid);
            body.put("wolf_month_rank",rank==null?0:rank + 1);
            body.put("wolf_month_score",score==null?0:score);
            //好人
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankGoodKey,uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankGoodKey,uid);
            body.put("good_rank",rank==null?0:rank + 1);
            body.put("good_score",score==null?0:score);
            //好人-日
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankGoodKey+":day:"+Dateutils.getlogindate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankGoodKey+":day:"+Dateutils.getlogindate(),uid);
            body.put("good_day_rank",rank==null?0:rank + 1);
            body.put("good_day_score",score==null?0:score);
            //好人-周
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankGoodKey+":weak:"+Dateutils.getDateWeaks(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankGoodKey+":weak:"+Dateutils.getDateWeaks(),uid);
            body.put("good_weak_rank",rank==null?0:rank + 1);
            body.put("good_weak_score",score==null?0:score);
            //好人-月
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankGoodKey+":month:"+Dateutils.getMonthDate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankGoodKey+":month:"+Dateutils.getMonthDate(),uid);
            body.put("good_month_rank",rank==null?0:rank + 1);
            body.put("good_month_score",score==null?0:score);
            //平民
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankCivyKey,uid);
            //score = rankJedis.zscore(RedisKeyPrefix.RankCivyKey,uid);
            body.put("civy_rank",rank==null?0:rank + 1);
            body.put("civy_score",vo.getCivyvictorynum());
            //女巫
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankMagicKey,uid);
            //score = rankJedis.zscore(RedisKeyPrefix.RankMagicKey,uid);
            body.put("magic_rank",rank==null?0:rank + 1);
            body.put("magic_score",vo.getMagicvictorynum());
            //守卫
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankGuardKey,uid);
            // = rankJedis.zscore(RedisKeyPrefix.RankGuardKey,uid);
            body.put("guard_rank",rank==null?0:rank + 1);
            body.put("guard_score",vo.getGuardvictorynum());
            //预言家
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankProphetKey,uid);
            //score = rankJedis.zscore(RedisKeyPrefix.RankProphetKey,uid);
            body.put("prophet_rank",rank==null?0:rank + 1);
            body.put("prophet_score",vo.getProphetvictorynum());
            //猎人
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankHunterKey,uid);
            //score = rankJedis.zscore(RedisKeyPrefix.RankHunterKey,uid);
            body.put("hunter_rank",rank==null?0:rank + 1);
            body.put("hunter_score",vo.getHuntervictorynum());

            //魅力
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankCharmKey,uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankCharmKey,uid);
            body.put("charm_rank",rank==null?0:rank + 1);
            body.put("charm_score",score==null?0:score);
            //魅力-日
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankCharmKey+":day:"+Dateutils.getlogindate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankCharmKey+":day:"+Dateutils.getlogindate(),uid);
            body.put("charm_day_rank",rank==null?0:rank + 1);
            body.put("charm_day_score",score==null?0:score);
            //魅力-周
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankCharmKey+":weak:"+Dateutils.getDateWeaks(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankCharmKey+":weak:"+Dateutils.getDateWeaks(),uid);
            body.put("charm_weak_rank",rank==null?0:rank + 1);
            body.put("charm_weak_score",score==null?0:score);
            //魅力-月
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankCharmKey+":month:"+Dateutils.getMonthDate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankCharmKey+":month:"+Dateutils.getMonthDate(),uid);
            body.put("charm_month_rank",rank==null?0:rank + 1);
            body.put("charm_month_score",score==null?0:score);

            //贡献
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankContributionKey,uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankContributionKey,uid);
            body.put("ctrl_rank",rank==null?0:rank + 1);
            body.put("ctrl_score",score==null?0:score);
            //贡献-日
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankContributionKey+":day:"+Dateutils.getlogindate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankContributionKey+":day:"+Dateutils.getlogindate(),uid);
            body.put("ctrl_day_rank",rank==null?0:rank + 1);
            body.put("ctrl_day_score",score==null?0:score);
            //贡献-周
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankContributionKey+":weak:"+Dateutils.getDateWeaks(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankContributionKey+":weak:"+Dateutils.getDateWeaks(),uid);
            body.put("ctrl_weak_rank",rank==null?0:rank + 1);
            body.put("ctrl_weak_score",score==null?0:score);
            //贡献-月
            rank = rankJedis.zrevrank(RedisKeyPrefix.RankContributionKey+":month:"+Dateutils.getMonthDate(),uid);
            score = rankJedis.zscore(RedisKeyPrefix.RankContributionKey+":month:"+Dateutils.getMonthDate(),uid);
            body.put("ctrl_month_rank",rank==null?0:rank + 1);
            body.put("ctrl_month_score",score==null?0:score);
        }catch (Exception e){
            LOG.error("getUserRank>>>",e);
        }finally {
            if (rankJedis != null){
                rankJedis.close();
            }
        }
        return body;
    }
    private final static int delTimeCD = 24*60*60*1000;
    private static long delTime = 0;
    public void delRankRedis(){
        Jedis rankJedis = null;
        try {
            rankJedis = jedisNoShardPool.getResource();
            //贡献-日
            rankJedis.del(RedisKeyPrefix.RankContributionKey + ":day:" + Dateutils.getYestodayLong());
            rankJedis.del(RedisKeyPrefix.RankContributionKey + ":weak:" + Dateutils.getPreviousDateWeaks());
            //魅力-日
            rankJedis.del(RedisKeyPrefix.RankCharmKey+":day:"+Dateutils.getYestodayLong());
            rankJedis.del(RedisKeyPrefix.RankCharmKey+":weak:"+Dateutils.getPreviousDateWeaks());
            //好人-日
            rankJedis.del(RedisKeyPrefix.RankGoodKey+":day:"+Dateutils.getYestodayLong());
            rankJedis.del(RedisKeyPrefix.RankGoodKey+":weak:"+Dateutils.getPreviousDateWeaks());
            //狼人-日
            rankJedis.del(RedisKeyPrefix.RankWolfKey+":day:"+Dateutils.getYestodayLong());
            rankJedis.del(RedisKeyPrefix.RankWolfKey+":weak:"+Dateutils.getPreviousDateWeaks());
            delTime = System.currentTimeMillis();
        }catch (Exception e){
            LOG.error("delRankRedis>>>",e);
        }finally {
            if (rankJedis != null){
                rankJedis.close();
            }
        }
    }


    public HashMap getTotalRank(){
        HashMap body = totalRankMap;
        try {
            if (totalRankMap.size()<1 || System.currentTimeMillis() - selts > intervalts){
                body = new HashMap();
                if (expRankVOList.size()>0){
                    body.put("exp",expRankVOList.get(0));
                }else {
                    List<RankVO> rankVOList = this.expRank();
                    if (rankVOList != null && rankVOList.size()>0){
                        body.put("exp",rankVOList.get(0));
                    }
                }

                body.put("exp_txt","所有经过的路，都是必经之路");

                /*if (wolfRankDayVOList.size()>0){
                    body.put("wolf",wolfRankDayVOList.get(0));
                }else {


                }*/
                List<RankVO> rankVOList = this.wolfRankTime("day");
                if (rankVOList!=null && rankVOList.size()>0){
                    body.put("wolf",rankVOList.get(0));
                }
                body.put("wolf_txt","夜，是我深邃的眼睛");

                /*if (goodRankDayVOList.size()>0){
                    body.put("good",goodRankDayVOList.get(0));
                }else {

                }*/
                List<RankVO> goodRankTime = this.goodRankTime("day");
                if (goodRankTime!=null && goodRankTime.size()>0){
                    body.put("good",goodRankTime.get(0));
                }

                body.put("good_txt","我是预言家，对跳标狼打！");

                List<UserInfoModel> userInfoModelList = this.charmRank("day");
                if (userInfoModelList!=null && userInfoModelList.size()>0){
                    body.put("charm",userInfoModelList.get(0));
                }
                body.put("charm_txt","班姬续史之姿，谢庭咏雪之态");

                List<UserInfoModel> ctrlUserInfoModel = this.ctrlRank("day");
                if (ctrlUserInfoModel!=null && ctrlUserInfoModel.size()>0){
                    body.put("ctrl",ctrlUserInfoModel.get(0));
                }
                body.put("ctrl_txt","我欲问鼎天下，试问谁与争锋");
                List<ContestConfig> contestList = ContestConfigLib.getinstance().getConfigList();
                HashMap integralRank = this.integralRank(contestList.get(0).getId(),2500,null);
                List<RankVO> rcontestankVOList = (List<RankVO>)integralRank.get("rankVOList");
                if (rcontestankVOList!=null && rcontestankVOList.size()>0){
                    body.put("contest",rcontestankVOList.get(0));
                }

                body.put("contest_txt","最强王者——狼人杀顶尖玩家");
                //
                totalRankMap = body;
                selts = System.currentTimeMillis();
                //每天第一次访问，清空前天的日榜
                if (System.currentTimeMillis() - delTime > delTimeCD){
                    delRankRedis();
                }
            }
        }catch (Exception e){
            LOG.error("getTotalRank>>>",e);
        }
        return body;
    }

    /**
     * 魅力榜
     * @param time
     * @return
     */
    public List<UserInfoModel> charmRank(String time){
        List<UserInfoModel> userModelList = new ArrayList<>();
        Jedis jedis =null;
        try {
            jedis = jedisNoShardPool.getResource();
            String key = RedisKeyPrefix.RankCharmKey;
            String datestr = "";
            if (time.equals("day")){
                datestr = ":" + time + ":" + Dateutils.getlogindate();
            }else if (time.equals("weak")){
                datestr = ":" + time + ":" + Dateutils.getDateWeaks();
            }else if (time.equals("month")){
                datestr = ":" + time + ":" + Dateutils.getMonthDate();
            }
            key = key + datestr;
            userModelList = getUserModelList(key,jedis,98,"charm");
        }catch (Exception e){
            LOG.error("charmRank>>>",e);
        }finally {
            if (jedis!=null){
                jedis.close();
            }
        }
        return userModelList;
    }

    /**
     * 个人贡献榜
     * @param time
     * @param uid
     * @param pagesize
     * @return
     */
    public List<UserInfoModel> privateCtrlRank(String time, String uid, int pagesize){
        List<UserInfoModel> userModelList = new ArrayList<>();
        Jedis jedis =null;
        try {
            jedis = jedisNoShardPool.getResource();
            String key = String.format(RedisKeyPrefix.RankprivateCtrlKey,uid);
            String datestr = "";
            if (time.equals("day")){
                datestr = ":" + time + ":" + Dateutils.getlogindate();
                //删除昨天的个人贡献榜
                jedis.del(key + ":" + time + ":" + Dateutils.getYestodayLong());
            }else if (time.equals("weak")){
                datestr = ":" + time + ":" + Dateutils.getDateWeaks();
                jedis.del(key + ":" + time + ":" + Dateutils.getPreviousDateWeaks());
            }else if (time.equals("month")){
                datestr = ":" + time + ":" + Dateutils.getMonthDate();
            }
            key = key + datestr;
            userModelList = getUserModelList(key,jedis,pagesize,"ctrl");
        }catch (Exception e){
            LOG.error("charmRank>>>",e);
        }finally {
            if (jedis!=null){
                jedis.close();
            }
        }
        return userModelList;
    }

    /**
     * 皇冠经验榜
     * @param time
     * @return
     */
    public List<UserInfoModel> ctrlRank(String time){
        List<UserInfoModel> userModelList = new ArrayList<>();
        Jedis jedis =null;
        try {
            jedis = jedisNoShardPool.getResource();
            String key = RedisKeyPrefix.RankContributionKey;
            String datestr = "";
            if (time.equals("day")){
                datestr = ":" + time + ":" + Dateutils.getlogindate();
            }else if (time.equals("weak")){
                datestr = ":" + time + ":" + Dateutils.getDateWeaks();
            }else if (time.equals("month")){
                datestr = ":" + time + ":" + Dateutils.getMonthDate();
            }
            key = key + datestr;

            userModelList = getUserModelList(key,jedis,98,"exp");
        }catch (Exception e){
            LOG.error("ctrlRank>>>",e);
        }finally {
            if (jedis!=null){
                jedis.close();
            }
        }
        return userModelList;
    }

    public List<UserInfoModel> getUserModelList(String key, Jedis jedis, int pagesize,String typename){
        List<UserInfoModel> userList = new ArrayList<>();
        if(jedis !=null){
            Set<Tuple> zrankWithScores = jedis.zrevrangeWithScores(key,0,pagesize);
            if (zrankWithScores != null) {
                List<String> keylist = new ArrayList<>();
                List<String> uidlist = new ArrayList<>();
                Map<String,Double> scoresmap = new HashMap<>();
                for (Tuple tuple : zrankWithScores) {
                    String uid = tuple.getElement();
                    //20170623 英宣 排行榜不许有负值
                    if (uid!=null && tuple.getScore()>0){
                        scoresmap.put(uid,tuple.getScore());
                        keylist.add(String.format(RedisKeyPrefix.UserInfoKey,uid));
                        uidlist.add(uid);
                    }
                }
                if (keylist.size()>0){
                    JedisPool jedisPool = RedisBucket.getInstance().getNoShardPool("user");
                    jedis = jedisPool.getResource();
                    List<String> rankvojson = jedis.mget(keylist.toArray(new String[keylist.size()]));
                    List<String> haveselectlist = new ArrayList<>();
                    for (String jsons : rankvojson){
                        if(jsons!=null){
                            UserInfoModel userInfoModel = JSON.parseObject(jsons,UserInfoModel.class);
                            if ("charm".equals(typename)){
                                userInfoModel.setCharmNum(scoresmap.get(userInfoModel.getUid()).intValue());
                            }else if ("ctrl".equals(typename)){
                                userInfoModel.setContribution(scoresmap.get(userInfoModel.getUid()).intValue());
                            }else if ("exp".equals(typename)){
                                userInfoModel.setCrownexp(scoresmap.get(userInfoModel.getUid()).longValue());
                            }
                            userList.add(userInfoModel);
                            haveselectlist.add(userInfoModel.getUid());
                        }

                    }
                    if (userList.size() != uidlist.size()){
                        for (String str : uidlist){
                            if (!haveselectlist.contains(str)){
                                UserInfoModel userInfoModel = UserService.getInstance().getUserInfo(str);
                                if (userInfoModel != null){
                                    if ("charm".equals(typename)){
                                        userInfoModel.setCharmNum(scoresmap.get(userInfoModel.getUid()).intValue());
                                    }else if ("ctrl".equals(typename)){
                                        userInfoModel.setContribution(scoresmap.get(userInfoModel.getUid()).intValue());
                                    }else if ("exp".equals(typename)){
                                        userInfoModel.setCrownexp(scoresmap.get(userInfoModel.getUid()).longValue());
                                    }
                                    userList.add(userInfoModel);
                                }
                            }
                        }
                    }
                }
            }
        }

        return userList;
    }
}
