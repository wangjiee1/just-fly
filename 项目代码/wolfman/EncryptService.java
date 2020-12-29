package wolfman.services;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dolphin.core.Pair;
import org.apache.log4j.Logger;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.body.MultipartBody;

import dolphin.core.Charsets;
import dolphin.core.Helpers;
import dolphin.core.LocalObject;
import wolfman.core.constant.ConstantData;
import wolfman.core.utils.AES;
import wolfman.core.utils.Base64;
import wolfman.core.utils.ConfigureEncryptAndDecrypt;
import wolfman.core.utils.RSA;
import wolfman.core.utils.RandomUtil;
import dolphin.uuid.TokenId;

/**
 * @author jackzhang
 *
 */
public class EncryptService {
	private final static Logger LOG = Logger
			.getLogger(EncryptService.class);
	private List<KeyPair> keyPairs;

	private final static EncryptService inst = new EncryptService();

	public static EncryptService getInstance() {
		return inst;
	}

	private static String secretKey = "ihB4S5ZK";
	public static SecretKeySpec deSecretKeySpec = new SecretKeySpec(secretKey.getBytes(), 0,
			secretKey.getBytes().length, "des");

//	public synchronized void generateKeyPairs() {
//		// 生成很慢，请异步执行，生成100个
//		// 定时刷新
//		KeyPairGenerator gen = LocalObject.rsaKeyGen.getDocument();
//		List<KeyPair> tempKeyPairs = new ArrayList<KeyPair>();
//		for (int i = 0; i < 100; i++) {
//			tempKeyPairs.add(gen.generateKeyPair());
//		}
//		keyPairs = tempKeyPairs;
//	}
	
	public synchronized void generateKeyPairs() {
		// 生成很慢，请异步执行，生成100个
		// 定时刷新
		List<KeyPair> tempKeyPairs = new ArrayList<KeyPair>();
		for (int i = 0; i < 100; i++) {
			KeyPair generateRandomKeyPair = RSA.generateRandomKeyPair();
			if(generateRandomKeyPair == null){
				LOG.error("generateKeyPairs gen one null keypair");
			}else{
				tempKeyPairs.add(generateRandomKeyPair);
			}
		}
		keyPairs = tempKeyPairs;
	}

	public static void main(String... args){
		String dd = "307adb36e5f3ea66a8684ed454a769e8831a080f2c9da40fd21b3e1b0d7de96a2f8f1ffcfe510ec73a2b72fdb63a9bd768f8a5d58705d69fdd820ddd178977b08bbad06854f25bf6a6db6da57635a648";
		Pair<Integer, String> integerStringPair = EncryptService.getInstance().authToken("10000478", dd);
	}

	/***
	 * 校验登录token的合法性
	 * @param clientId
	 * @param loginToken
	 * @return 0 通过 1 token数据结构不合法 2 token数据内容不合法 3 token已失效
	 */
	public dolphin.core.Pair<Integer,String> authToken(String clientId, String loginToken) {
		if(loginToken == null||clientId == null){
			return new dolphin.core.Pair<Integer,String>(1,"token数据结构不合法");
		}
		byte[] bytes = fromHex(loginToken);
		String token = EncryptService.getInstance().decryptByDefaultDes(bytes);
		if (token == null) {
			return new dolphin.core.Pair<Integer,String>(1,"token数据结构不合法");
		}
		String[] params = token.split("#");
		if (params != null && params.length >= 3) {
			String tokenUid = params[1];
			long ts = Long.valueOf(params[2]);
			if(!clientId.equals(tokenUid) && !tokenUid.equals(ConstantData.ladderRaceTokenSalt)){
				return new dolphin.core.Pair<Integer,String>(2,"token数据内容不合法");
			}else if(System.currentTimeMillis() - ts > ConstantData.expireLoginTokenDuration){
				return new dolphin.core.Pair<Integer,String>(3,"token已失效");
			}

		}else{
			return new dolphin.core.Pair<Integer,String>(2,"token数据内容不合法");
		}
		return new dolphin.core.Pair<Integer,String>(0,token);
	}

	/***
	 * 校验登录token的合法性
	 * @param clientId
	 * @param loginToken
	 * @return 0 通过 1 token数据结构不合法 2 token数据内容不合法 3 token已失效
	 */
	public dolphin.core.Pair<Integer,String> authTokenByEnterRoomPass(String clientId,String password, String loginToken) {
		if(loginToken == null||clientId == null){
			return new dolphin.core.Pair<Integer,String>(1,"token数据结构不合法");
		}
		byte[] bytes = fromHex(loginToken);
		String token = EncryptService.getInstance().decryptByDefaultDes(bytes);
		if (token == null) {
			return new dolphin.core.Pair<Integer,String>(1,"token数据结构不合法");
		}
		String[] params = token.split("#");
		if (params != null && params.length >= 3) {
			String tokenUid = params[1];
			long ts = Long.valueOf(params[2]);
			String pass = params.length >= 4?params[3]:"";
			if(!clientId.equals(tokenUid)){
				return new dolphin.core.Pair<Integer,String>(2,"token数据内容不合法");
			}else if(System.currentTimeMillis() - ts > ConstantData.expireLoginTokenDuration){
				return new dolphin.core.Pair<Integer,String>(3,"token已失效");
			}else if(!password.equals(pass)){
				return new dolphin.core.Pair<Integer,String>(4,"秘密不匹配");
			}
		}else{
			return new dolphin.core.Pair<Integer,String>(2,"token数据内容不合法");
		}
		return new dolphin.core.Pair<Integer,String>(0,token);
	}

	public KeyPair randomKeyPair() {
		int index = LocalObject.random.get().nextInt(keyPairs.size());
		return keyPairs.get(index);
	}

	public byte[] encryptWithPublicKey(PublicKey encryptKey, String content) {
		Cipher cipher = LocalObject.rsaCipher.get();
		try {
			cipher.init(Cipher.ENCRYPT_MODE, encryptKey);
			return cipher.doFinal(content.getBytes(Charsets.utf8));
		} catch (Exception e) {
			return null;
		}
	}

	public byte[] decryptWithPrivateKey(PrivateKey decryptKey, byte[] encryptData) {
		Cipher cipher = LocalObject.rsaCipher.get();
		try {
			cipher.init(Cipher.DECRYPT_MODE, decryptKey);
			return cipher.doFinal(encryptData);
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * 解密base64编码的公钥加密字符串
	 * @param decryptKey
	 * @param encryptDataAndBase64
	 * @return
	 */
	public byte[] decryptWithPrivateKey(PrivateKey decryptKey, String encryptDataAndBase64) {
		Cipher cipher = LocalObject.rsaCipher.get();
		try {
			cipher.init(Cipher.DECRYPT_MODE, decryptKey);
			byte[] fromHex = Base64.decodeBase64(encryptDataAndBase64.getBytes(ConfigureEncryptAndDecrypt.CHAR_ENCODING));
			return cipher.doFinal(fromHex);
		} catch (Exception e) {
			return null;
		}
	}
	
	/**
	 * 解密base64编码的公钥加密字符串
	 * @param decryptKey
	 * @param encryptDataAndBase64
	 * @return
	 */
	public String decryptWithPrivateKeyWithBase64(PrivateKey decryptKey, String encryptDataAndBase64) {
		byte[] decryptWithPrivateKey = decryptWithPrivateKey(decryptKey,encryptDataAndBase64);
		return new String(decryptWithPrivateKey);
	}

	public byte[] encryptByDes(SecretKey secretKey, String content) {
		Cipher cipher = LocalObject.desCipher.get();
		try {
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);
			return cipher.doFinal(content.getBytes(Charsets.utf8));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public String decryptByDes(SecretKey secretKey, byte[] encryptData) {
		Cipher cipher = LocalObject.desCipher.get();
		try {
			cipher.init(Cipher.DECRYPT_MODE, secretKey);
			byte[] bytes = cipher.doFinal(encryptData);
			return new String(bytes, Charsets.utf8);
		} catch (Exception e) {
			return null;
		}
	}

	public byte[] encryptByDefaultDes(String content) {
		Cipher cipher = LocalObject.desCipher.get();
		try {
			cipher.init(Cipher.ENCRYPT_MODE, deSecretKeySpec);
			return cipher.doFinal(content.getBytes(Charsets.utf8));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public String decryptByDefaultDes(byte[] encryptData) {
		if(encryptData == null){
			return null;
		}
		Cipher cipher = LocalObject.desCipher.get();
		try {
			cipher.init(Cipher.DECRYPT_MODE, deSecretKeySpec);
			byte[] bytes = cipher.doFinal(encryptData);
			return new String(bytes, Charsets.utf8);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 转成16进制
	 * @param input
	 * @return
	 */
	public String toHex(byte input[]) {
		if (input == null)
			return null;
		StringBuffer output = new StringBuffer(input.length * 2);
		for (int i = 0; i < input.length; i++) {
			int current = input[i] & 0xff;
			if (current < 16)
				output.append("0");
			output.append(Integer.toString(current, 16));
		}

		return output.toString();
	}

	/**
	 * 还原成字节数组
	 * @param input
	 * @return
	 */
	public byte[] fromHex(String input) {
		if (input == null)
			return null;
		byte output[] = new byte[input.length() / 2];
		try {
			for (int i = 0; i < output.length; i++)
				output[i] = (byte) Integer.parseInt(input.substring(i * 2, (i + 1) * 2), 16);
		} catch (Exception e) {
			LOG.error(String.format("fromHex->input:%s", input),e);
			return null;
		}
		return output;
	}

	/**
	 * 解密
	 */
	public String decode(String str) {
		int strlen = str.length();
		int keylen = secretKey.length();
		char[] chararr = str.toCharArray();
		char[] keyarr = secretKey.toCharArray();
		for (int i = 0; i < strlen; i++) {
			for (int j = 0; j < keylen; j++) {
				chararr[i] = (char) (chararr[i] ^ keyarr[j]);
			}
		}
		return new String(chararr);
	}

	
	/**
	 * 得到公钥
	 * @throws Exception
	 */
	public static PublicKey getPublicKey(byte[] key) throws Exception {
		X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey publicKey = keyFactory.generatePublic(keySpec);
		return publicKey;
	}
	
	/**
	 * 得到私钥
	 * 
	 * @param key
	 *            密钥字符串（经过base64编码）
	 * @throws Exception
	 */
	public static PrivateKey getPrivateKey(String key) throws Exception {
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(
				(key.getBytes()));
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
		return privateKey;
	}
	
//	public static void main(String[] args) throws Exception {
//		try {
//
//			String matchtoken = TokenId.nextId() + "#" + "100000" + "#" + System.currentTimeMillis();
//			byte[] matchsi = wolfman.services.EncryptService.getInstance().
//					encryptByDefaultDes(matchtoken);
//			String matchhex = wolfman.services.EncryptService.getInstance().toHex(matchsi);
//			System.out.println(matchhex);
//
//			String token = "hellodesjack";
//			byte[] si = EncryptService.getInstance().encryptByDefaultDes(token);
//			String hex3 = EncryptService.getInstance().toHex(si);
//			System.err.println(hex3);
//
//			String osName = System.getProperty("os.name");
//			String osVerison = System.getProperty("os.version");
//			String ss = "({ddd:\"ddddd\"})";
//			int hashCode = ss.hashCode();
//			int start = ss.indexOf("{");
//			int end = ss.lastIndexOf("}");
//			String jsonString = ss.substring(start, end+1);
//			JSONObject jsonObject = JSON.parseObject(jsonString);
//
//			int continuous = 9;
//			int position1 = (continuous >= 8)? 8 :continuous;
//			int position2 = (continuous % 15 == 0) ? 15 : -1;
//
//
//
//
//			String target = null;
//			Unirest.post("http://101.201.211.24:8000/service/random_for_h5")
//			.field("target", target).asJsonAsync();
//			MultipartBody field = Unirest.post("http://101.201.211.24:8000/service/random_for_h5")
//					.field("target", target);
//			System.err.println(field.getHttpRequest().getUrl());
//			HttpResponse<JsonNode> asJson = field.asJson();
//			System.err.println(asJson);
//		} catch (Exception e) {
//			// TODO: handle exception
//			LOG.error(e.toString());
//		}
//
//
//
////		String token = "dfsdfffdgdgsg";
//		String clientId = "123456789";
//		String token = TokenId.nextId() + "#" + clientId;
//		token = "hellodes";
//		byte[] si = EncryptService.getInstance().encryptByDefaultDes(token);
//		String hex3 = EncryptService.getInstance().toHex(si);
//		byte[] fromHex2 = EncryptService.getInstance().fromHex(hex3);
//		String decryptByDefaultDes = EncryptService.getInstance().decryptByDefaultDes(fromHex2);
//		System.err.println(decryptByDefaultDes);
//
//		String tt = "NTBfMDEyMzNGODE5RTZFNzVCMjVGOUJDRkFFNDM5NzEyQkZfMl9EMUNBQTQyQi01NDBELTExRTYtODYzNS1BNDVFNjBGMkZBRTlfMTQ2OTYzMjg0NA==";
//		byte[] fromHex3 = EncryptService.getInstance().fromHex(tt);
//		String decryptByDefaultDes2 = EncryptService.getInstance().decryptByDefaultDes(fromHex3);
//		System.err.println(decryptByDefaultDes2);
//
//		Calendar calendar = Calendar.getInstance();
//        calendar.setTimeInMillis(System.currentTimeMillis());
//        String dateKey = String.format("%d:%d", calendar.get(Calendar.YEAR),calendar.get(Calendar.MONTH)+1);
////		final int signSummaryInfo = OtherRedisService.getInstance().getSignSummaryInfoByMonth(uid,dateKey);
//
//        Integer monthPre = 2208;
//		System.err.println(Integer.toBinaryString(monthPre));
//
//		System.err.println(dateKey);
//
//
//		System.err.println(Byte.valueOf((byte) -95).equals((byte) 161));
//		byte[] encrypt = AES.encrypt("the quick brown fox jumped onSuccess the lazy dog", "jacklovekaiqiboy");
//		System.err.println(new String(Base64.encode(encrypt)));
//		String hex2 = EncryptService.getInstance().toHex(encrypt);
//		System.err.println(">>>>"+ hex2);
//		System.err.println(">>>>"+ Arrays.toString(encrypt));
//		EncryptService.getInstance().generateKeyPairs();
//
////		String xujin_orinal = "iOS Developer Tips encoded in Base64";
////		byte[] bytes2 = xujin_orinal.getBytes(ConfigureEncryptAndDecrypt.CHAR_ENCODING);
////		byte[] encode = Base64.encodeBase64(bytes2);
////		System.err.println(new String(encode,ConfigureEncryptAndDecrypt.CHAR_ENCODING));
////		String hex2 = EncryptService.getInstance().toHex(bytes2);
////		System.err.println(hex2);
////
////		String xujin = "D30819f300d06092a864886f70d010101050003818d00308189028181009d11e68b38e3239e5f679314b1ebb51e099999b6713ee327e46fdeba4829e35f19418cfba2112d17c6f734ad4fab45d43d65c99cb1b9ae1512a3213b51c818075a8b8dcea1d9fd0a3bf0e8b5d75a947be514de10717b27b71b979db2fad1fea7fe8f5ec79e9e0fd84fd421484eb3e6a47c13ca5e8ff168806fcf0738974e88bb0203010001";
////		byte[] fromHex3 = EncryptService.getInstance().fromHex(xujin);
////		System.err.println(Arrays.toString(fromHex3));
//
//		KeyPair keyPair = EncryptService.getInstance().randomKeyPair();
//		PrivateKey private1 = keyPair.getPrivate();
//		PublicKey encryptKey = keyPair.getPublic();
//
//		byte[] encoded2 = encryptKey.getEncoded();
//		PublicKey publicKey2 = EncryptService.getPublicKey(encoded2);
//		System.err.println(publicKey2.equals(encryptKey));
//
//
//
//
//
//
//
//		byte[] bytes = Base64.encodeBase64(encoded2);
//
////		String pubString = EncryptService.getInstance().toHex(encoded2);
////		byte[] bytes = pubString.getBytes(ConfigureEncryptAndDecrypt.CHAR_ENCODING);
//		System.err.println(new String(bytes,ConfigureEncryptAndDecrypt.CHAR_ENCODING));
//
////		byte[] fromHex2 = EncryptService.getInstance().fromHex(pubString);
//		byte[] decodeBase64 = Base64.decodeBase64(bytes);
////		PublicKey publicKey2 = EncryptService.getPublicKey(decodeBase64);
//
//
//		System.err.println(publicKey2.equals(encryptKey));
//		//随机生成AES密钥
//		String aesKey = RandomUtil.getRandom(16);
//		System.err.println(aesKey);
//		byte[] encryptWithPublicKey2 = EncryptService.getInstance().encryptWithPublicKey(publicKey2, aesKey);
//		byte[] encodeBase64 = Base64.encodeBase64(encryptWithPublicKey2);
//		String toServer = new String(encodeBase64,ConfigureEncryptAndDecrypt.CHAR_ENCODING);
//
//		String aesKeyExcept = EncryptService.getInstance().decryptWithPrivateKeyWithBase64(private1, toServer);
//		System.err.println(aesKeyExcept);
//
//
//
//
//		String salt = "tinypig-secret";
//		String appKey = "tinypig";
//		String openId = "1000000000";
//		String sign = Helpers.signParams(salt, "appKey=" + appKey,"openId="+openId);
//		if (!Helpers.checkSign(salt, sign, "appKey=" + appKey,
//				"openId=" + openId)) {
//			System.err.println("signature mismatch");
//			return;
//		}
//
//		EncryptService.getInstance().generateKeyPairs();
//		//exchange secryt
//		KeyPair randomKeyPair = EncryptService.getInstance().randomKeyPair();
//		byte[] encoded = randomKeyPair.getPublic().getEncoded();
//		String hex = EncryptService.getInstance().toHex(encoded);
//
//
//		String sec = "adsadaaffsdsdsd";
//		byte[] encryptByDefaultDes = EncryptService.getInstance().encryptByDefaultDes(sec);
//
//
//		String encodeSec = "8c9663f3fab8656a41bdd736734accc176952a7c551bc5e3fc53c0a8a0f69cb3354968fe0457e924bb3210110122353e91da4febf6a539d3c9a41a8c20214d936754ebf4894ff265389f38f190f0df19b8e083a9716e22596559e496ae797a38b58a1097c76b3b409c62c8fc08cc15307e6c5aa73d27ff4a3af4e4b9644fabe2";
//		byte[] fromHex = EncryptService.getInstance().fromHex(encodeSec);
//
//		//client getDocument publicKey
//		PublicKey publicKey;
//		try {
//			publicKey = getPublicKey(encoded);
//			byte[] encryptWithPublicKey = EncryptService.getInstance().encryptWithPublicKey(publicKey, hex);
//			String privateKeyString = "-----BEGIN PRIVATE KEY-----\nMIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAMMjZu9UtVitvgHS\ntpmAU/rRVdhy9GaT2rnpCJOYSb0deVI+rXPKHI9Aca2LkWiRgkzM1wqbRvAvWrqK\ngm4PgQUjnoNr7vRd1HPUKNA9ATfJetddW86yar0ux3FMVaxUFN6F0KatqkplVXHo\n8qXubKHRx9dCbK95P96rJkrWBiO9AgMBAAECgYBO1UKEdYg9pxMX0XSLVtiWf3Na\n2jX6Ksk2Sfp5BhDkIcAdhcy09nXLOZGzNqsrv30QYcCOPGTQK5FPwx0mMYVBRAdo\nOLYp7NzxW/File//169O3ZFpkZ7MF0I2oQcNGTpMCUpaY6xMmxqN22INgi8SHp3w\nVU+2bRMLDXEc/MOmAQJBAP+Sv6JdkrY+7WGuQN5O5PjsB15lOGcr4vcfz4vAQ/uy\nEGYZh6IO2Eu0lW6sw2x6uRg0c6hMiFEJcO89qlH/B10CQQDDdtGrzXWVG457vA27\nkpduDpM6BQWTX6wYV9zRlcYYMFHwAQkE0BTvIYde2il6DKGyzokgI6zQyhgtRJ1x\nL6fhAkB9NvvW4/uWeLw7CHHVuVersZBmqjb5LWJU62v3L2rfbT1lmIqAVr+YT9CK\n2fAhPPtkpYYo5d4/vd1sCY1iAQ4tAkEAm2yPrJzjMn2G/ry57rzRzKGqUChOFrGs\nlm7HF6CQtAs4HC+2jC0peDyg97th37rLmPLB9txnPl50ewpkZuwOAQJBAM/eJnFw\nF5QAcL4CYDbfBKocx82VX/pFXng50T7FODiWbbL4UnxICE0UBFInNNiWJxNEb6jL\n5xd0pcy9O2DOeso=\n-----END PRIVATE KEY-----";
//			byte[] decryptWithPrivateKey = EncryptService.getInstance().decryptWithPrivateKey(getPrivateKey(privateKeyString), fromHex);
//			System.err.println(decryptWithPrivateKey);
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//
//
//		Map<Integer, String> levelMap = new TreeMap<Integer, String>(new Comparator<Integer>() {
//			@Override
//			public int compare(Integer o1, Integer o2) {
//				return o1 - o2;
//			}
//		});
//		levelMap.put(1, "111");
//		levelMap.put(2, "222");
//		// System.err.println(levelMap.values());
//		for (Iterator iterator = levelMap.values().iterator(); iterator.hasNext();) {
//			System.err.println(iterator.next());
//		}
//
//		ArrayList<String> vvv = new ArrayList<String>();
//		vvv.add("11");
//		vvv.add("222");
//		for (Iterator<String> iteratorForDaily = vvv.iterator(); iteratorForDaily.hasNext();) {
//			String next = iteratorForDaily.next();
//			if (next.equals("11")) {
//				iteratorForDaily.remove();
//			}
//		}
//		System.err.println(vvv);
//
//		HashMap<String, Object> map = new HashMap<String, Object>();
//		map.put("dd", null);
//		String jsonString = JSON.toJSONString(map);
//		System.err.println(jsonString);
//		List<String> aaa = Arrays.asList(new String[] { "sss", "ddd", "fff" });
//		int counter = 0;
//		for (Iterator iterator = aaa.iterator(); iterator.hasNext();) {
//			String string = (String) iterator.next();
//			string = "ddfffff";
//			aaa.set(counter++, string);
//		}
//
//		for (Iterator iterator = aaa.iterator(); iterator.hasNext();) {
//			String string = (String) iterator.next();
//			System.err.println(string);
//		}
//
//		System.err.println("ok");
//
//		// System.err.println(new Date());
//		// List<String> ssList = Arrays.asList("ss","ddd");
//		// System.err.println(ssList);
//		// // 当前时间
//		// while(true){
//		// Long nowtime = System.currentTimeMillis() / 1000;
//		// System.err.println(nowtime);
//		// try {
//		// Thread.sleep(1000);
//		// } catch (InterruptedException e) {
//		// e.printStackTrace();
//		// }
//		// }
//
//		/**
//		 * bean{id,name,type,description,conditions,rewards,finish_mode,state{
//		 * 可接受|可领取|已领取}} tomcat内存中存取
//		 * operation{task_list(登录时候获取);task_process_commit;task_finished}
//		 *
//		 * task_process_bean{id,conditionState,state} 进度状态列表
//		 * {%s(player:)%s(daily|newbie)accepted桶;%s(player:)%s(daily|newbie)
//		 * finished桶}
//		 *
//		 */
//		// System.err.println(Integer.MAX_VALUE);
//		// int seconds = Integer.MAX_VALUE;
//		// String node = "/dolphin/php/service0";
//		// String[] pathArray = node.split("/");
//		// String sid = pathArray[pathArray.length - 1];
//		SecretKey key = LocalObject.desKeyGen.get().generateKey();
//		String s = "fsdfdf资本黄色的反倒是sdfdsfdsfsdfsdfsdfafwerweg";
//
//		CharSequence subSequence = s.subSequence(0, 13);
//		System.err.println(subSequence.toString());
//		s = s + s + s + s;
//		s = s + s + s + s;
//		s = s + s + s + s;
//		s = s + s + s + s;
//		s = s + s + s + s;
//
////		String clientId = "123456789";
////		String token = TokenId.nextId() + "#" + clientId;
////
////		byte[] si = EncryptService.getInstance().encryptByDes(deSecretKeySpec, token);
////		System.err.println(new String(si));
////		// si[1] = 34;
////		String decrypt = EncryptService.getInstance().decryptByDes(deSecretKeySpec, si);
////		System.out.println(decrypt);
////
////		System.err.println(token + "len:" + token.length());
//	}

}
