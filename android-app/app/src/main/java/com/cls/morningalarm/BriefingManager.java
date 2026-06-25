package com.cls.morningalarm;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BriefingManager {

    private Handler mainHandler;

    public interface BriefingCallback {
        void onSuccess(String title, String date, String content);
        void onError(String error);
    }

    public BriefingManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void fetchMorningBriefing(final BriefingCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                    String urlStr = "https://newsapi.eastmoney.com/kuaixun/v1/getlist_102_ajaxResult_50_1_.html";

                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    conn.setRequestProperty("Referer", "https://www.eastmoney.com/");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        String jsonStr = sb.toString();
                        int jsonStart = jsonStr.indexOf("{");
                        int jsonEnd = jsonStr.lastIndexOf("}");
                        if (jsonStart >= 0 && jsonEnd > jsonStart) {
                            jsonStr = jsonStr.substring(jsonStart, jsonEnd + 1);
                        }

                        JSONObject jsonObject = new JSONObject(jsonStr);
                        JSONArray newsList = jsonObject.optJSONArray("LivesList");

                        if (newsList != null && newsList.length() > 0) {
                            List<String> newsItems = new ArrayList<>();
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            String today = sdf.format(new Date());

                            for (int i = 0; i < Math.min(newsList.length(), 20); i++) {
                                JSONObject item = newsList.optJSONObject(i);
                                if (item != null) {
                                    String showTime = item.optString("showtime", "");
                                    if (showTime.startsWith(today)) {
                                        String title = item.optString("title", "");
                                        String digest = item.optString("digest", "");

                                        String newsItem = "";
                                        if (title != null && !title.isEmpty()) {
                                            newsItem = title;
                                        }
                                        if (digest != null && !digest.isEmpty() && !digest.equals(title)) {
                                            if (!newsItem.isEmpty()) {
                                                newsItem += "。";
                                            }
                                            newsItem += digest;
                                        }

                                        if (!newsItem.isEmpty()) {
                                            newsItems.add(newsItem);
                                        }
                                    }
                                }
                            }

                            if (newsItems.isEmpty()) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onError("今日暂无早报数据");
                                    }
                                });
                            } else {
                                StringBuilder sb2 = new StringBuilder();
                                sb2.append("早上好，现在为您播报今日财经早报。");
                                sb2.append("今天是").append(dateStr).append("。");
                                sb2.append("以下是今日重要财经资讯：");

                                for (int i = 0; i < newsItems.size(); i++) {
                                    sb2.append("第").append(i + 1).append("条，");
                                    sb2.append(newsItems.get(i));
                                    sb2.append("。");
                                }

                                sb2.append("以上就是今日早报的主要内容，祝您一天愉快。");

                                final String title = "财经早报 " + dateStr;
                                final String contentStr = sb2.toString();
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(title, dateStr, contentStr);
                                    }
                                });
                            }
                        } else {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError("数据格式错误");
                                }
                            });
                        }
                    } else {
                        final int code = responseCode;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("请求失败: " + code);
                            }
                        });
                    }
                    conn.disconnect();
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("请求异常: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }
}
