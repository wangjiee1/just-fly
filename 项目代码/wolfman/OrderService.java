package wolfman.services;

import org.apache.log4j.Logger;
import wolfman.room.state.Dateutils;
import wolfman.web.dao.PayDao;
import wolfman.web.dao.UserDao;
import wolfman.web.dao.UserLoginDao;
import wolfman.web.dao.domain.UserOrderVO;
import wolfman.web.dao.domain.UserRetainedVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 充值查询
 * Created by wsc on 2017/6/28.
 */
public class OrderService extends BaseService{

    private final static Logger LOG = Logger
            .getLogger(OrderService.class);

    private static class  OrderServiceHandler{
        private static OrderService cache = new OrderService();
    }

    public static OrderService getInstance(){
        return OrderServiceHandler.cache;
    }

    private static long selts = 0;
    private final static int intervalts = 30*1000;
    private static List<UserOrderVO> userOrderVOList = new ArrayList<>();
    private static List<UserRetainedVO> userRetainedVOList = new ArrayList<>();

    public List<UserOrderVO> getUserPayOrderList(String monthstr,int os){
        if (System.currentTimeMillis() - selts > intervalts){
            userOrderVOList = new ArrayList<>();
            Map<String,UserOrderVO> map = new HashMap<>();
            List<String> monthlist = Dateutils.getDayListOfMonth(monthstr);
            for (String str : monthlist){
                map.put(str,new UserOrderVO());
            }
            String year = Dateutils.getYear();
            if (year.equals("2017") && Integer.valueOf(monthstr)<4){
                return userOrderVOList;
            }
            String yearmonth = year + monthstr;
            map = PayDao.getInstance().getUserPayOrderList(os,yearmonth,map);
            for (String str : monthlist){
                UserOrderVO userOrderVO = map.get(str);
                userOrderVO.setDatestr(str);
                userOrderVOList.add(userOrderVO);
            }
            selts = System.currentTimeMillis();
        }
        return userOrderVOList;
    }

    /**
     * 留存
     * @param monthstr
     * @param os
     */
    public List<UserRetainedVO> getRetainedList(String monthstr,int os,String channel,String packageName){
        if (System.currentTimeMillis() - selts > intervalts){
            userRetainedVOList = new ArrayList<>();
            /**
             * 本月多少天
             * 注册的用户
             * 每天的登录信息放入map<日期,List<UID>>
             * 循环，每天不同日期的留存
             */
            List<String> monthlist = Dateutils.getDayListOfMonth(monthstr);
            Map<String,List<String>> registmap = new HashMap<>();
            Map<String,List<String>> loginmap = new HashMap<>();
            for (String str : monthlist){
                registmap.put(str,new ArrayList<String>());
                loginmap.put(str,new ArrayList<String>());
            }
            registmap = UserLoginDao.getInstance().getRetainedList(registmap,os,1,monthlist.get(0),monthlist.get(monthlist.size()-1),channel,packageName);
            loginmap = UserLoginDao.getInstance().getRetainedList(loginmap,os,2,monthlist.get(0),monthlist.get(monthlist.size()-1),channel,packageName);
            for (String str : monthlist){
                UserRetainedVO userRetainedVO = new UserRetainedVO();
                userRetainedVO.setDatestr(str);
                List<String> registList = registmap.get(str);
                List<String> loginList = loginmap.get(str);
                if (loginList!=null){
                    userRetainedVO.setActive_num(loginList.size());
                }
                if (registList!=null){
                    userRetainedVO.setNew_num(registList.size());
                    List<String> dayRetainedList2 = new ArrayList<>();
                    List<String> templist = loginmap.get(Dateutils.getDayForNum(str,1));
                    if (templist!=null){
                        dayRetainedList2.addAll(templist);
                        dayRetainedList2.retainAll(registList);
                        userRetainedVO.setNum_2(dayRetainedList2.size());
                    }
                    List<String> dayRetainedList3 = new ArrayList<>();
                    templist = loginmap.get(Dateutils.getDayForNum(str,2));
                    if (templist!=null){
                        dayRetainedList3.addAll(templist);
                        dayRetainedList3.retainAll(registList);
                        userRetainedVO.setNum_3(dayRetainedList3.size());
                    }
                    List<String> dayRetainedList5 = new ArrayList<>();
                    templist = loginmap.get(Dateutils.getDayForNum(str,4));
                    if (templist!=null){
                        dayRetainedList5.addAll(templist);
                        dayRetainedList5.retainAll(registList);
                        userRetainedVO.setNum_5(dayRetainedList5.size());
                    }
                    List<String> dayRetainedList7 = new ArrayList<>();
                    templist = loginmap.get(Dateutils.getDayForNum(str,6));
                    if (templist!=null){
                        dayRetainedList7.addAll(templist);
                        dayRetainedList7.retainAll(registList);
                        userRetainedVO.setNum_7(dayRetainedList7.size());
                    }
                    List<String> dayRetainedList15 = new ArrayList<>();
                    templist = loginmap.get(Dateutils.getDayForNum(str,14));
                    if (templist!=null){
                        dayRetainedList15.addAll(templist);
                        dayRetainedList15.retainAll(registList);
                        userRetainedVO.setNum_15(dayRetainedList15.size());
                    }
                    List<String> dayRetainedList30 = new ArrayList<>();
                    templist = loginmap.get(Dateutils.getDayForNum(str,29));
                    if (templist!=null){
                        dayRetainedList30.addAll(templist);
                        dayRetainedList30.retainAll(registList);
                        userRetainedVO.setNum_30(dayRetainedList30.size());
                    }
                }
                userRetainedVOList.add(userRetainedVO);
            }
            selts = System.currentTimeMillis();
        }

        return userRetainedVOList;
    }
}
