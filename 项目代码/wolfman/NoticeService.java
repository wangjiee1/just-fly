package wolfman.services;

import org.apache.http.client.utils.DateUtils;
import org.apache.log4j.Logger;
import wolfman.dictionary.lib.BarnConfigLib;
import wolfman.dictionary.lib.NoticeConfigLib;
import wolfman.room.state.Dateutils;
import wolfman.web.dao.AdminLogDao;
import wolfman.web.dao.UserLoginDao;
import wolfman.web.dao.domain.BarnVO;
import wolfman.web.dao.domain.NoticeVO;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mac on 17/7/13.
 */
public class NoticeService extends BaseService{
    private final static Logger LOG = Logger
            .getLogger(NoticeService.class);




    private static class NoticeServiceHandler{
        private static NoticeService noticeService = new NoticeService();
    }

    public static NoticeService getInstance(){
        return NoticeServiceHandler.noticeService;
    }

    public List<NoticeVO> getNoticeListAdmin(){
        //return NoticeConfigLib.getNoticeVOList();
        List<NoticeVO> noticeVoList = UserLoginDao.getInstance().getNoticeVoList();
        for (NoticeVO vo : noticeVoList){
            if (vo.getType() == 0){
                vo.setTypestr("无");
            }else if (vo.getType() == 1){
                vo.setTypestr("重要");
            }else if (vo.getType() == 2){
                vo.setTypestr("商城");
            }else if (vo.getType() == 3){
                vo.setTypestr("活动");
            }else if (vo.getType() == 4){
                vo.setTypestr("更新");
            }else if (vo.getType() == 5){
                vo.setTypestr("新闻");
            }

            if (vo.getOs() == 1){
                vo.setOsstr("android");
            }else if (vo.getOs() == 2){
                vo.setOsstr("iphone");
            }else if (vo.getOs() == 11){
                vo.setOsstr("应用宝");
            }

            vo.setStartstr(Dateutils.longTimeToString2(vo.getStart()));
            vo.setEndstr(Dateutils.longTimeToString2(vo.getEnd()));
            vo.setCtimestr(Dateutils.longTimeToString2(vo.getCtime()));
            long nowdate = System.currentTimeMillis()/1000;
            if (vo.getStart() < nowdate && vo.getEnd()> nowdate){
                vo.setIsEffect("是");
            }else {
                vo.setIsEffect("过期");
            }
        }
        return noticeVoList;
    }

    public List<NoticeVO> getNoticeListWeb(String channel,String packageName,Integer os){
        //NoticeConfigLib.loadAllNoticePool();
        List<NoticeVO> resultlist = new ArrayList<>();
        List<NoticeVO> noticeVoList = NoticeConfigLib.getNoticeVOList();
        if (os!=null){
            if (os == 1){
                noticeVoList = NoticeConfigLib.getNoticeVOList1();
            }else if (os == 2){
                noticeVoList = NoticeConfigLib.getNoticeVOList2();
            }else if (os == 11){
                noticeVoList = NoticeConfigLib.getNoticeVOList11();
            }
        }
        if (channel != null && noticeVoList !=null&& !channel.isEmpty()){
            for (NoticeVO noticeVO : noticeVoList){
                if (noticeVO.getChannel()==null ||noticeVO.getChannel().trim().isEmpty() ||
                        noticeVO.getChannel().trim().equals(channel)){
                    resultlist.add(noticeVO);
                }
            }
        }else {
            resultlist = noticeVoList;
        }
        return resultlist;
    }

    public void delNotice(String id){
        UserLoginDao.getInstance().delNotice(id);
        //NoticeConfigLib.loadAllNoticePool();
    }
    public void editNotice(String add_type, String id,
                           String title, String start,
                           String end, String content,
                           Integer sort, Integer type,
                           Integer os,String channel,
                           String packageName) {
        if (add_type!=null){
            String colourjson = "";
            if (add_type.equals("add")){
                UserLoginDao.getInstance().addNotice(title, Dateutils.getDateForString2(start),
                        Dateutils.getDateForString2(end),content,sort,type,colourjson,
                        os,channel,packageName);
            }else if (add_type.equals("update")){
                UserLoginDao.getInstance().editNotice(id,title, Dateutils.getDateForString2(start),
                        Dateutils.getDateForString2(end),content,sort,type,colourjson,
                        os,channel,packageName);
            }
        }
        //NoticeConfigLib.loadAllNoticePool();
    }




    public List<BarnVO> getBarnListAdmin(){
        List<BarnVO> noticeVoList = UserLoginDao.getInstance().getBarnVoList();
        for (BarnVO vo : noticeVoList){

            vo.setStartstr(Dateutils.longTimeToString2(vo.getStart()));
            vo.setEndstr(Dateutils.longTimeToString2(vo.getEnd()));
            vo.setCtimestr(Dateutils.longTimeToString2(vo.getCtime()));
            long nowdate = System.currentTimeMillis()/1000;
            if (vo.getStart() < nowdate && vo.getEnd()> nowdate){
                vo.setIsEffect("是");
            }else {
                vo.setIsEffect("过期");
            }
        }
        return noticeVoList;
    }

    public void delBarn(String id){
        UserLoginDao.getInstance().delBarn(id);
    }

    public void editBarn(String add_type, String id, String start, String end, String content, Integer sort, String colour, Integer os, String channel, String packageName) {
        if (add_type!=null){
            if (add_type.equals("add")){
                UserLoginDao.getInstance().addBarn(colour, Dateutils.getDateForString2(start),
                        Dateutils.getDateForString2(end),content,sort,
                        os,channel,packageName);
            }else if (add_type.equals("update")){
                UserLoginDao.getInstance().editBarn(id,colour, Dateutils.getDateForString2(start),
                        Dateutils.getDateForString2(end),content,sort,
                        os,channel,packageName);
            }
        }
    }

    public List<BarnVO> getBarnListWeb(String channel,String packageName,Integer os){
        List<BarnVO> resultlist = new ArrayList<>();
        //BarnConfigLib.loadAllBarnPool();
        List<BarnVO> barnVOList = BarnConfigLib.getBarnVOList();
        if (os!=null){
            if (os == 1){
                barnVOList = BarnConfigLib.getBarnVOList1();
            }else if (os == 2){
                barnVOList = BarnConfigLib.getBarnVOList2();
            }else if (os == 11){
                barnVOList = BarnConfigLib.getBarnVOList11();
            }
        }
        if (channel != null && barnVOList !=null && !channel.isEmpty()){
            for (BarnVO barnVO : barnVOList){
                if (barnVO.getChannel()==null ||barnVO.getChannel().trim().isEmpty() ||
                        barnVO.getChannel().trim().equals(channel)){
                    resultlist.add(barnVO);
                }
            }
        }else {
            resultlist = barnVOList;
        }
        return resultlist;
    }
}
