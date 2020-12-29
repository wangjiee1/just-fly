package com.peanut.commons.pay.iapppay;

import net.sf.json.JSONObject;

public class IAppPayUtil {
	/*
	 * CP服务端组装请求参数,请求下单url，得到 返回的 transid。把 transid 传到客户端进行支付。
	 * 服务端组装下单请求参数：transdata
	 * ={"appid":"500000185","waresid":2,"cporderid":"1421206966472"
	 * ,"price":1,"currency"
	 * :"RMB","appuserid":"A100003A832D40","cpprivateinfo":"cpprivateinfo123456"
	 * ,"notifyurl":"http://192.168.0.140:8094/monizhuang/api?type=100"}&sign=
	 * PNkLyWO5dxzZJrGNRJhSQGJ1oRMpvNDOHmQJntCt7OP3faT6oyL3Jc4Ne6r4IyJMxm3CAk1rxiQBoSuuAf06zsoEWbT4pNIkgqyafP4ai7zKfkJxeX7gsiG6wycT3PqRlwtmF0L7W4RDicrnAGrOQ3ynUxsrGW4oJ
	 * +7dKdHM4ZA=&signtype=RSA 请求地址：以文档给出的为准 再此请格外注意 每个参数值的 数据类型 可选参数
	 * ：waresname price cpprivateinfo notifyurl
	 */
	/**
	 * 组装请求参数
	 * 
	 * @param appid
	 *            应用编号
	 * @param waresid
	 *            商品编号
	 * @param price
	 *            商品价格
	 * @param waresname
	 *            商品名称
	 * @param cporderid
	 *            商户订单号
	 * @param appuserid
	 *            用户编号
	 * @param cpprivateinfo
	 *            商户私有信息
	 * @param notifyurl
	 *            支付结果通知地址
	 * @return 返回组装好的用于post的请求数据 .................
	 */
	public static String ReqData(String waresname, String cporderid, float price, String appuserid,
			String cpprivateinfo, String notifyurl) throws Exception {
		String json;
		json = "appid:";
		json += IAppPaySDKConfig.APP_ID;
		json += " userid:";
		json += appuserid;
		json += " waresid:";
		json += IAppPaySDKConfig.WARES_ID_1;
		json += "cporderid:";
		json += cporderid;
		System.out.println("json=" + json);

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("appid", IAppPaySDKConfig.APP_ID);
		jsonObject.put("waresid", IAppPaySDKConfig.WARES_ID_1);
		jsonObject.put("cporderid", cporderid);
		jsonObject.put("currency", "RMB");
		jsonObject.put("appuserid", appuserid);
		// 以下是参数列表中的可选参数
		if (!waresname.isEmpty()) {
			jsonObject.put("waresname", waresname);
		}
		/*
		 * 当使用的是 开放价格策略的时候 price的值是 程序自己 设定的价格，使用其他的计费策略的时候 price 不用传值
		 */
		jsonObject.put("price", price);
		if (!cpprivateinfo.isEmpty()) {
			jsonObject.put("cpprivateinfo", cpprivateinfo);
		}
		if (!notifyurl.isEmpty()) {
			/*
			 * 如果此处不传同步地址，则是以后台传的为准。
			 */
			jsonObject.put("notifyurl", notifyurl);
		}
		String content = jsonObject.toString();// 组装成 json格式数据
		// 调用签名函数 重点注意： 请一定要阅读 sdk
		// 包中的爱贝AndroidSDK3.4.4\03-接入必看-服务端接口说明及范例\爱贝服务端接入指南及示例0311\IApppayCpSyncForJava
		// \接入必看.txt
		String sign = SignHelper.sign(content, IAppPaySDKConfig.APPV_KEY);
		String data = "transdata=" + content + "&sign=" + sign
				+ "&signtype=RSA";// 组装请求参数
		System.out.println("请求数据:" + data);
		return data;
	}
}
