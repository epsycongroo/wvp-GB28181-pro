package com.genersoft.iot.vmp.media.zlm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.gb28181.bean.*;
import com.genersoft.iot.vmp.gb28181.event.EventPublisher;
import com.genersoft.iot.vmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommanderFroPlatform;
import com.genersoft.iot.vmp.media.zlm.dto.*;
import com.genersoft.iot.vmp.service.*;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.storager.IVideoManagerStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommander;

import javax.servlet.http.HttpServletRequest;

/**    
 * @description:针对 ZLMediaServer的hook事件监听
 * @author: swwheihei
 * @date:   2020年5月8日 上午10:46:48     
 */
@RestController
@RequestMapping("/index/hook")
public class ZLMHttpHookListener {

	private final static Logger logger = LoggerFactory.getLogger(ZLMHttpHookListener.class);

	@Autowired
	private SIPCommander cmder;

	@Autowired
	private SIPCommanderFroPlatform commanderFroPlatform;

	@Autowired
	private IPlayService playService;

	@Autowired
	private IVideoManagerStorage storager;

	@Autowired
	private IRedisCatchStorage redisCatchStorage;

	@Autowired
	private IMediaServerService mediaServerService;

	@Autowired
	private IStreamProxyService streamProxyService;

	@Autowired
	private IStreamPushService streamPushService;

	@Autowired
	private IMediaService mediaService;

	@Autowired
	private EventPublisher eventPublisher;

	 @Autowired
	 private ZLMMediaListManager zlmMediaListManager;

	@Autowired
	private ZLMHttpHookSubscribe subscribe;

	@Autowired
	private UserSetting userSetting;

	@Autowired
	private IUserService userService;

	@Autowired
	private VideoStreamSessionManager sessionManager;

	@Autowired
	private AssistRESTfulUtils assistRESTfulUtils;

	@Qualifier("taskExecutor")
	@Autowired
	private ThreadPoolTaskExecutor taskExecutor;

	/**
	 * 服务器定时上报时间，上报间隔可配置，默认10s上报一次
	 *
	 */
	@ResponseBody
	@PostMapping(value = "/on_server_keepalive", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onServerKeepalive(@RequestBody JSONObject json){

		logger.info("[ ZLM HOOK ] on_server_keepalive API调用，参数：" + json.toString());
		String mediaServerId = json.getString("mediaServerId");
		List<ZLMHttpHookSubscribe.Event> subscribes = this.subscribe.getSubscribes(HookType.on_server_keepalive);
		if (subscribes != null  && subscribes.size() > 0) {
			for (ZLMHttpHookSubscribe.Event subscribe : subscribes) {
				subscribe.response(null, json);
			}
		}
		mediaServerService.updateMediaServerKeepalive(mediaServerId, json.getJSONObject("data"));

		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}

	/**
	 * 流量统计事件，播放器或推流器断开时并且耗用流量超过特定阈值时会触发此事件，阈值通过配置文件general.flowThreshold配置；此事件对回复不敏感。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_flow_report", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onFlowReport(@RequestBody JSONObject json){
		
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_flow_report API调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * 访问http文件服务器上hls之外的文件时触发。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_http_access", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onHttpAccess(@RequestBody JSONObject json){
		
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_http_access API 调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("err", "");
		ret.put("path", "");
		ret.put("second", 600);
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * 播放器鉴权事件，rtsp/rtmp/http-flv/ws-flv/hls的播放都将触发此鉴权事件。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_play", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onPlay(@RequestBody OnPlayHookParam param){

		JSONObject json = (JSONObject)JSON.toJSON(param);

		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_play API调用，参数：" + JSON.toJSONString(param));
		}
		String mediaServerId = param.getMediaServerId();
		ZLMHttpHookSubscribe.Event subscribe = this.subscribe.sendNotify(HookType.on_play, json);
		if (subscribe != null ) {
			MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);
			if (mediaInfo != null) {
				subscribe.response(mediaInfo, json);
			}
		}
		JSONObject ret = new JSONObject();
		if (!"rtp".equals(param.getApp())) {
			Map<String, String> paramMap = urlParamToMap(param.getParams());
			StreamAuthorityInfo streamAuthorityInfo = redisCatchStorage.getStreamAuthorityInfo(param.getApp(), param.getStream());
			if (streamAuthorityInfo == null
					|| (streamAuthorityInfo.getCallId() != null && !streamAuthorityInfo.getCallId().equals(paramMap.get("callId")))) {
				ret.put("code", 401);
				ret.put("msg", "Unauthorized");
				return new ResponseEntity<>(ret.toString(),HttpStatus.OK);
			}
		}

		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * rtsp/rtmp/rtp推流鉴权事件。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_publish", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onPublish(@RequestBody OnPublishHookParam param) {

		JSONObject json = (JSONObject) JSON.toJSON(param);

		logger.info("[ ZLM HOOK ]on_publish API调用，参数：" + json.toString());
		JSONObject ret = new JSONObject();
		String mediaServerId = json.getString("mediaServerId");
		MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);
		if (!"rtp".equals(param.getApp())) {
			// 推流鉴权
			if (param.getParams() == null) {
				logger.info("推流鉴权失败： 缺少不要参数：sign=md5(user表的pushKey)");
				ret.put("code", 401);
				ret.put("msg", "Unauthorized");
				return new ResponseEntity<>(ret.toString(), HttpStatus.OK);
			}
			Map<String, String> paramMap = urlParamToMap(param.getParams());
			String sign = paramMap.get("sign");
			if (sign == null) {
				logger.info("推流鉴权失败： 缺少不要参数：sign=md5(user表的pushKey)");
				ret.put("code", 401);
				ret.put("msg", "Unauthorized");
				return new ResponseEntity<>(ret.toString(), HttpStatus.OK);
			}
			// 推流自定义播放鉴权码
			String callId = paramMap.get("callId");
			// 鉴权配置
			boolean hasAuthority = userService.checkPushAuthority(callId, sign);
			if (!hasAuthority) {
				logger.info("推流鉴权失败： sign 无权限: callId={}. sign={}", callId, sign);
				ret.put("code", 401);
				ret.put("msg", "Unauthorized");
				return new ResponseEntity<>(ret.toString(), HttpStatus.OK);
			}
			StreamAuthorityInfo streamAuthorityInfo = StreamAuthorityInfo.getInstanceByHook(param);
			streamAuthorityInfo.setCallId(callId);
			streamAuthorityInfo.setSign(sign);
			// 鉴权通过
			redisCatchStorage.updateStreamAuthorityInfo(param.getApp(), param.getStream(), streamAuthorityInfo);
			// 通知assist新的callId
			taskExecutor.execute(()->{
				if (mediaInfo != null && mediaInfo.getRecordAssistPort() > 0) {
					assistRESTfulUtils.addStreamCallInfo(mediaInfo, param.getApp(), param.getStream(), callId, null);
				}
			});

		}else {
			zlmMediaListManager.sendStreamEvent(param.getApp(),param.getStream(), param.getMediaServerId());
		}

		ret.put("code", 0);
		ret.put("msg", "success");
		ret.put("enable_hls", true);
		if (!"rtp".equals(param.getApp())) {
			ret.put("enable_audio", true);
		}


		ZLMHttpHookSubscribe.Event subscribe = this.subscribe.sendNotify(HookType.on_publish, json);
		if (subscribe != null) {
			if (mediaInfo != null) {
				subscribe.response(mediaInfo, json);
			}else {
				ret.put("code", 1);
				ret.put("msg", "zlm not register");
			}
		}

		if ("rtp".equals(param.getApp())) {
			ret.put("enable_mp4", userSetting.getRecordSip());
		}else {
			ret.put("enable_mp4", userSetting.isRecordPushLive());
		}
		List<SsrcTransaction> ssrcTransactionForAll = sessionManager.getSsrcTransactionForAll(null, null, null, param.getStream());
		if (ssrcTransactionForAll != null && ssrcTransactionForAll.size() == 1) {
			String deviceId = ssrcTransactionForAll.get(0).getDeviceId();
			String channelId = ssrcTransactionForAll.get(0).getChannelId();
			DeviceChannel deviceChannel = storager.queryChannel(deviceId, channelId);
			if (deviceChannel != null) {
				ret.put("enable_audio", deviceChannel.isHasAudio());
			}
			// 如果是录像下载就设置视频间隔十秒
			if (ssrcTransactionForAll.get(0).getType() == VideoStreamSessionManager.SessionType.download) {
				ret.put("mp4_max_second", 10);
				ret.put("enable_mp4", true);
				ret.put("enable_audio", true);

			}
		}



		return new ResponseEntity<String>(ret.toString(), HttpStatus.OK);
	}



	/**
	 * 录制mp4完成后通知事件；此事件对回复不敏感。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_record_mp4", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onRecordMp4(@RequestBody JSONObject json){
		
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_record_mp4 API调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	/**
	 * 录制hls完成后通知事件；此事件对回复不敏感。
	 *
	 */
	@ResponseBody
	@PostMapping(value = "/on_record_ts", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onRecordTs(@RequestBody JSONObject json){

		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_record_ts API调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * rtsp专用的鉴权事件，先触发on_rtsp_realm事件然后才会触发on_rtsp_auth事件。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_rtsp_realm", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onRtspRealm(@RequestBody JSONObject json){
		
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_rtsp_realm API调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("realm", "");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	
	
	/**
	 * 该rtsp流是否开启rtsp专用方式的鉴权事件，开启后才会触发on_rtsp_auth事件。需要指出的是rtsp也支持url参数鉴权，它支持两种方式鉴权。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_rtsp_auth", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onRtspAuth(@RequestBody JSONObject json){
		
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_rtsp_auth API调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("encrypted", false);
		ret.put("passwd", "test");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * shell登录鉴权，ZLMediaKit提供简单的telnet调试方式，使用telnet 127.0.0.1 9000能进入MediaServer进程的shell界面。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_shell_login", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onShellLogin(@RequestBody JSONObject json){
		
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_shell_login API调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		ZLMHttpHookSubscribe.Event subscribe = this.subscribe.sendNotify(HookType.on_shell_login, json);
		if (subscribe != null ) {
			MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);
			if (mediaInfo != null) {
				subscribe.response(mediaInfo, json);
			}

		}

		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * rtsp/rtmp流注册或注销时触发此事件；此事件对回复不敏感。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_stream_changed", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onStreamChanged(@RequestBody MediaItem item){

		logger.info("[ ZLM HOOK ]on_stream_changed API调用，参数：" + JSONObject.toJSONString(item));
		String mediaServerId = item.getMediaServerId();
		JSONObject json = (JSONObject) JSON.toJSON(item);
		ZLMHttpHookSubscribe.Event subscribe = this.subscribe.sendNotify(HookType.on_stream_changed, json);
		if (subscribe != null ) {
			MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);
			if (mediaInfo != null) {
				subscribe.response(mediaInfo, json);
			}
		}
		// 流消失移除redis play
		String app = item.getApp();
		String stream = item.getStream();
		String schema = item.getSchema();
		List<MediaItem.MediaTrack> tracks = item.getTracks();
		boolean regist = item.isRegist();
		if (item.getOriginType() == OriginType.RTMP_PUSH.ordinal()
				|| item.getOriginType() == OriginType.RTSP_PUSH.ordinal()
				|| item.getOriginType() == OriginType.RTC_PUSH.ordinal()) {
			if (regist) {
				StreamAuthorityInfo streamAuthorityInfo = redisCatchStorage.getStreamAuthorityInfo(app, stream);
				if (streamAuthorityInfo == null) {
					streamAuthorityInfo = StreamAuthorityInfo.getInstanceByHook(item);
				}else {
					streamAuthorityInfo.setOriginType(item.getOriginType());
					streamAuthorityInfo.setOriginTypeStr(item.getOriginTypeStr());
				}
				redisCatchStorage.updateStreamAuthorityInfo(app, stream, streamAuthorityInfo);
			}else {
				redisCatchStorage.removeStreamAuthorityInfo(app, stream);
			}
		}

		if ("rtsp".equals(schema)){
			logger.info("on_stream_changed：注册->{}, app->{}, stream->{}", regist, app, stream);
			if (regist) {
				mediaServerService.addCount(mediaServerId);
			}else {
				mediaServerService.removeCount(mediaServerId);
			}
			if (item.getOriginType() == OriginType.PULL.ordinal()
					|| item.getOriginType() == OriginType.FFMPEG_PULL.ordinal()) {
				// 设置拉流代理上线/离线
				streamProxyService.updateStatus(regist, app, stream);
			}
			if ("rtp".equals(app) && !regist ) {
				StreamInfo streamInfo = redisCatchStorage.queryPlayByStreamId(stream);
				if (streamInfo!=null){
					redisCatchStorage.stopPlay(streamInfo);
					storager.stopPlay(streamInfo.getDeviceID(), streamInfo.getChannelId());
					// 如果正在给上级推送，则发送bye

				}else{
					streamInfo = redisCatchStorage.queryPlayback(null, null, stream, null);
					if (streamInfo != null) {
						redisCatchStorage.stopPlayback(streamInfo.getDeviceID(), streamInfo.getChannelId(),
								streamInfo.getStream(), null);
					}
					// 如果正在给上级推送，则发送bye
				}
			}else {
				if (!"rtp".equals(app)){
					String type = OriginType.values()[item.getOriginType()].getType();
					MediaServerItem mediaServerItem = mediaServerService.getOne(mediaServerId);

					if (mediaServerItem != null){
						if (regist) {
							StreamAuthorityInfo streamAuthorityInfo = redisCatchStorage.getStreamAuthorityInfo(app, stream);
							StreamInfo streamInfoByAppAndStream = mediaService.getStreamInfoByAppAndStream(mediaServerItem,
									app, stream, tracks, streamAuthorityInfo.getCallId());
							item.setStreamInfo(streamInfoByAppAndStream);
							redisCatchStorage.addStream(mediaServerItem, type, app, stream, item);
							if (item.getOriginType() == OriginType.RTSP_PUSH.ordinal()
									|| item.getOriginType() == OriginType.RTMP_PUSH.ordinal()
									|| item.getOriginType() == OriginType.RTC_PUSH.ordinal() ) {
								item.setSeverId(userSetting.getServerId());
								zlmMediaListManager.addPush(item);
							}
						}else {
							// 兼容流注销时类型从redis记录获取
							MediaItem mediaItem = redisCatchStorage.getStreamInfo(app, stream, mediaServerId);
							if (mediaItem != null) {
								type = OriginType.values()[mediaItem.getOriginType()].getType();
								redisCatchStorage.removeStream(mediaServerItem.getId(), type, app, stream);
							}
							GbStream gbStream = storager.getGbStream(app, stream);
							if (gbStream != null) {
//								eventPublisher.catalogEventPublishForStream(null, gbStream, CatalogEvent.OFF);
							}
							zlmMediaListManager.removeMedia(app, stream);
						}
						if (type != null) {
							// 发送流变化redis消息
							JSONObject jsonObject = new JSONObject();
							jsonObject.put("serverId", userSetting.getServerId());
							jsonObject.put("app", app);
							jsonObject.put("stream", stream);
							jsonObject.put("register", regist);
							jsonObject.put("mediaServerId", mediaServerId);
							redisCatchStorage.sendStreamChangeMsg(type, jsonObject);
						}
					}
				}
			}
		}

		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * 流无人观看时事件，用户可以通过此事件选择是否关闭无人看的流。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_stream_none_reader", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onStreamNoneReader(@RequestBody JSONObject json){

		logger.info("[ ZLM HOOK ]on_stream_none_reader API调用，参数：" + json.toString());
		String mediaServerId = json.getString("mediaServerId");
		String streamId = json.getString("stream");
		String app = json.getString("app");
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		if ("rtp".equals(app)){
			ret.put("close", true);
			StreamInfo streamInfoForPlayCatch = redisCatchStorage.queryPlayByStreamId(streamId);
			if (streamInfoForPlayCatch != null) {
				// 收到无人观看说明流也没有在往上级推送
				if (redisCatchStorage.isChannelSendingRTP(streamInfoForPlayCatch.getChannelId())) {
					List<SendRtpItem> sendRtpItems = redisCatchStorage.querySendRTPServerByChnnelId(streamInfoForPlayCatch.getChannelId());
					if (sendRtpItems.size() > 0) {
						for (SendRtpItem sendRtpItem : sendRtpItems) {
							ParentPlatform parentPlatform = storager.queryParentPlatByServerGBId(sendRtpItem.getPlatformId());
							commanderFroPlatform.streamByeCmd(parentPlatform, sendRtpItem.getCallId());
						}
					}
				}
				cmder.streamByeCmd(streamInfoForPlayCatch.getDeviceID(), streamInfoForPlayCatch.getChannelId(),
						streamInfoForPlayCatch.getStream(), null);
				redisCatchStorage.stopPlay(streamInfoForPlayCatch);
				storager.stopPlay(streamInfoForPlayCatch.getDeviceID(), streamInfoForPlayCatch.getChannelId());
			}else{
				StreamInfo streamInfoForPlayBackCatch = redisCatchStorage.queryPlayback(null, null, streamId, null);
				if (streamInfoForPlayBackCatch != null) {
					cmder.streamByeCmd(streamInfoForPlayBackCatch.getDeviceID(),
							streamInfoForPlayBackCatch.getChannelId(), streamInfoForPlayBackCatch.getStream(), null);
					redisCatchStorage.stopPlayback(streamInfoForPlayBackCatch.getDeviceID(),
							streamInfoForPlayBackCatch.getChannelId(), streamInfoForPlayBackCatch.getStream(), null);
				}else {
					StreamInfo streamInfoForDownload = redisCatchStorage.queryDownload(null, null, streamId, null);
					// 进行录像下载时无人观看不断流
					if (streamInfoForDownload != null) {
						ret.put("close", false);
					}
				}
			}
			MediaServerItem mediaServerItem = mediaServerService.getOne(mediaServerId);
			if (mediaServerItem != null && mediaServerItem.getStreamNoneReaderDelayMS() == -1) {
				ret.put("close", false);
			}
			return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
		}else {
			StreamProxyItem streamProxyItem = streamProxyService.getStreamProxyByAppAndStream(app, streamId);
			if (streamProxyItem != null && streamProxyItem.isEnable_remove_none_reader()) {
				ret.put("close", true);
				streamProxyService.del(app, streamId);
				String url = streamProxyItem.getUrl() != null?streamProxyItem.getUrl():streamProxyItem.getSrc_url();
				logger.info("[{}/{}]<-[{}] 拉流代理无人观看已经移除",  app, streamId, url);
			}else {
				ret.put("close", false);
			}
			return new ResponseEntity<String>(ret.toString(),HttpStatus.OK);
		}
	}
	
	/**
	 * 流未找到事件，用户可以在此事件触发时，立即去拉流，这样可以实现按需拉流；此事件对回复不敏感。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_stream_not_found", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onStreamNotFound(@RequestBody JSONObject json){
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_stream_not_found API调用，参数：" + json.toString());
		}
		String mediaServerId = json.getString("mediaServerId");
		MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);
		if (userSetting.isAutoApplyPlay() && mediaInfo != null && mediaInfo.isRtpEnable()) {
			String app = json.getString("app");
			String streamId = json.getString("stream");
			if ("rtp".equals(app)) {
				String[] s = streamId.split("_");
				if (s.length == 2) {
					String deviceId = s[0];
					String channelId = s[1];
					Device device = redisCatchStorage.getDevice(deviceId);
					if (device != null) {
						playService.play(mediaInfo,deviceId, channelId, null, null, null);
					}
				}
			}
		}

		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<>(ret.toString(),HttpStatus.OK);
	}
	
	/**
	 * 服务器启动事件，可以用于监听服务器崩溃重启；此事件对回复不敏感。
	 *  
	 */
	@ResponseBody
	@PostMapping(value = "/on_server_started", produces = "application/json;charset=UTF-8")
	public ResponseEntity<String> onServerStarted(HttpServletRequest request, @RequestBody JSONObject jsonObject){
		
		if (logger.isDebugEnabled()) {
			logger.debug("[ ZLM HOOK ]on_server_started API调用，参数：" + jsonObject.toString());
		}
		String remoteAddr = request.getRemoteAddr();
		jsonObject.put("ip", remoteAddr);
		List<ZLMHttpHookSubscribe.Event> subscribes = this.subscribe.getSubscribes(HookType.on_server_started);
		if (subscribes != null  && subscribes.size() > 0) {
			for (ZLMHttpHookSubscribe.Event subscribe : subscribes) {
				subscribe.response(null, jsonObject);
			}
		}

		ZLMServerConfig zlmServerConfig = JSONObject.toJavaObject(jsonObject, ZLMServerConfig.class);
		if (zlmServerConfig !=null ) {
			mediaServerService.zlmServerOnline(zlmServerConfig);
		}
		JSONObject ret = new JSONObject();
		ret.put("code", 0);
		ret.put("msg", "success");
		return new ResponseEntity<>(ret.toString(),HttpStatus.OK);
	}

	private Map<String, String> urlParamToMap(String params) {
		HashMap<String, String> map = new HashMap<>();
		if (ObjectUtils.isEmpty(params)) {
			return map;
		}
		String[] paramsArray = params.split("&");
		if (paramsArray.length == 0) {
			return map;
		}
		for (String param : paramsArray) {
			String[] paramArray = param.split("=");
			if (paramArray.length == 2){
				map.put(paramArray[0], paramArray[1]);
			}
		}
		return map;
	}
}
