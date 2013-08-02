package com.joy.launcher.network.impl;

import java.io.InputStream;

import org.json.JSONObject;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;

import com.joy.launcher.cache.ImageOption;
import com.joy.launcher.network.handler.BitmapHandler;
import com.joy.launcher.network.handler.WallpaperHandler;
import com.joy.launcher.network.util.ClientHttp;
import com.joy.launcher.network.util.ClientInterface;
import com.joy.launcher.network.util.Protocal;
import com.joy.launcher.util.Constants;

/**
 * 联网接口的具体实现
 * @author wanghao
 *
 */
public class Service implements ServiceInterface {
	private static Service service;
	ClientInterface cs = null;
	ProtocalFactory pfactory;

	// 类似于AsycTask类
	public interface CallBack {
		/**
		 * 在doInBackground之前被调用，这里是联网前，更新UI
		 */
		public void onPreExecute();

		/**
		 * 在doInBackground之后被调用，更新UI
		 */
		public void onPostExecute();

		/**
		 * 处理后台耗时事情，不可在此更新UI
		 */
		public void doInBackground();
	}

	private Service() {
	};

	public static synchronized Service getInstance() throws Exception {
		if (service == null) {
			service = new Service();
			service.cs = new ClientHttp();
			service.pfactory = new ProtocalFactory();
		}
		return service;
	}

	public void GotoNetwork(final CallBack callBack) {

		final Handler handler = new Handler() {
			public void handleMessage(Message message) {

				int what = message.what;
				switch (what) {
				case 0:
					callBack.onPreExecute();
					break;
				case 1:
					callBack.onPostExecute();
					break;
				}
			}
		};
		new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				handler.sendEmptyMessage(0);
				callBack.doInBackground();
				handler.sendEmptyMessage(1);
			}
		}).start();
		;
	}

	// --------------------------------------------------------------------------
	/**
	 * 释放网络资源
	 */
	@Override
	public void shutdownNetwork() {
		cs.shutdownNetwork();
		cs = null;
		service = null;
	}

	@Override
	public boolean netWorkIsOK() {
		return cs.isOK();
	}

	@Override
	public String getTestData() throws Exception {
		// TODO Auto-generated method stub
		String url = Constants.TEST_URL;
		Protocal protocal = pfactory.bitmapProtocal(url);
		JSONObject json = cs.request(protocal);
		if (json == null) {
			return null;
		}
		return json.toString();
	}

	@Override
	public Bitmap getBitmapByUrl(String url, ImageOption... option) {
		// TODO Auto-generated method stub
		Protocal protocal = pfactory.bitmapProtocal(url);
		InputStream in = cs.getInputStream(protocal);
		BitmapHandler bhandler = new BitmapHandler();
		Bitmap bp = bhandler.getBitmapByUrl(in, url, option);
		return bp;
	}

	@Override
	public void getWallpaper() throws Exception {
		// TODO Auto-generated method stub
		Protocal protocal = pfactory.wallpaperProtocal();
		JSONObject result = cs.request(protocal);
		WallpaperHandler whandler = new WallpaperHandler();
		whandler.getWallpaperType(result);
	}
}
