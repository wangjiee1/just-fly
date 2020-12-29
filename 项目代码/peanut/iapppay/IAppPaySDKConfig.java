package com.peanut.commons.pay.iapppay;

/**
 *应用接入iAppPay云支付平台sdk集成信息 
 */
public class IAppPaySDKConfig{

	/**
	 * 应用名称：
	 * 应用在iAppPay云支付平台注册的名称
	 */
	public final static  String APP_NAME = "花生娱乐";

	/**
	 * 应用编号：
	 * 应用在iAppPay云支付平台的编号，此编号用于应用与iAppPay云支付平台的sdk集成 
	 */
	public final static  String APP_ID = "3007756550";

	/**
	 * 商品编号：
	 * 应用的商品在iAppPay云支付平台的编号，此编号用于iAppPay云支付平台的sdk到iAppPay云支付平台查找商品详细信息（商品名称、商品销售方式、商品价格）
	 * 编号对应商品名称为：创别书城小说
	 */
	public final static  int WARES_ID_1=1;

	/**
	 * 应用私钥：
	 * 用于对商户应用发送到平台的数据进行加密
	 */
	public final static String APPV_KEY = "MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAJiVoixiDvenqQ/8GK+bvfqneB1Jjq+codzXSuieEkJ9mT2DRzun0MeN/c6A+5DXGqU8q7oLKTYux6ffKKK1WRjLh6+sffwStpOPLbBqSx/b9/DphBiLG3O3x26eGlRZwJUNEwQUH8Wwid7tSLExrmI6mvWH2kso9KLaYrXZNE5tAgMBAAECgYBSVJr228o7swm0CFDBoIV31BkppGELPLbR1JEkxvYPTnn7CPTMqZYrtt/2Jv6WfppwAjae4hrQk5lDaVQhxzxVHXTs9JSb9dhSBimS88pTT1wlnrHXtNAM6FXv+DozYHyfnrHvX3j2a6PS41QaJS3aZOywe4YR/OohRLRYEvVEgQJBAO9qsLrbuePtzmEDWsZdLsK6XAYJrstl61S+fZgC7lB4ymRysgwsdEGNzOiUQo/gINR0SChTFmZZ8u5fchKF/XECQQCjJz5v5Gw9jPWM8lY9E8Cj6HuD/oeG7Mk0qunxRQYyKyqOjZlM0uLl2p7Yc0u231Q+rInIXYK4sytn3gxtulK9AkB0nMHB8d4ED8NS62Bed+qbvEuwQS0bMuRB3Zqs3NiY54ylClyAo3Jor5mbuwMEswUqlgzDX1zss2kpA+I69XpxAkBwDJar39OangFR0Gj7v2IQ76xMZXUMa/hvTGYohgAQWmb6yjKbeUXNGEz5WI2KRWylMnfZ/Ka3VI2d4vjkLw99AkEAvrYPOsU+2ar5VouHv6hIjLCtwHU9KZ1W5utegUGK2Z8YNvW/eji3+fy2R33iQo5JTsCx9MVYAbvAGP0aFMOyrA==";

	/**
	 * 平台公钥：
	 * 用于商户应用对接收平台的数据进行解密
	
	 */
	public final static String PLATP_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC54baVyldVDWMVVoFBkRVuml6JBkmi0xCXtsSBNoaknmhbDjDdjNxoxhN8K8h8VQITo+P5VwphhEhM7wn5bPfkD3cxIHBHEOguAT5766EOY+OToysDCPzXRusrzei6GIArhxlpbmzzvTzQDZfrdb5qTme8lvV+sP4+Ft8hsYubhwIDAQAB";
	
	/**
	 * 爱贝支付回调
	 */
	public final static String NOTIFYURL = "pay/iapppay_callback";
}