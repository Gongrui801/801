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
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BriefingManager {

    private Handler mainHandler;

    public static class BriefingData {
        public String title;
        public String date;
        public String content;
        public String audioUrl;
        public String articleId;
    }

    public interface BriefingCallback {
        void onSuccess(BriefingData data);
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
                    BriefingData data = fetchFromCLS();
                    if (data != null) {
                        final BriefingData finalData = data;
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(finalData);
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("获取早报失败");
                            }
                        });
                    }
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

    private BriefingData fetchFromCLS() throws Exception {
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String subjectUrl = "https://www.cls.cn/subject/1151";
        String html = fetchHtml(subjectUrl, "https://www.cls.cn/");

        JSONObject nextData = extractNextData(html);
        if (nextData == null) {
            throw new Exception("无法解析页面数据");
        }

        JSONObject dataObj = nextData.optJSONObject("props")
                .optJSONObject("pageProps")
                .optJSONObject("data");

        if (dataObj == null) {
            throw new Exception("数据结构错误");
        }

        JSONArray articles = dataObj.optJSONArray("articles");
        if (articles == null || articles.length() == 0) {
            throw new Exception("暂无早报文章");
        }

        JSONObject latestArticle = null;
        long latestTime = 0;
        String today = dateStr;

        for (int i = 0; i < articles.length(); i++) {
            JSONObject article = articles.optJSONObject(i);
            if (article != null) {
                long articleTime = article.optLong("article_time", 0);
                String articleDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .format(new Date(articleTime * 1000L));

                if (articleDate.equals(today)) {
                    if (articleTime > latestTime) {
                        latestTime = articleTime;
                        latestArticle = article;
                    }
                }
            }
        }

        if (latestArticle == null && articles.length() > 0) {
            latestArticle = articles.optJSONObject(0);
            latestTime = latestArticle.optLong("article_time", 0);
            dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(new Date(latestTime * 1000L));
        }

        if (latestArticle == null) {
            throw new Exception("未找到早报文章");
        }

        String articleId = latestArticle.optString("article_id", "");
        String title = latestArticle.optString("article_title", "");
        String brief = latestArticle.optString("article_brief", "");

        BriefingData briefingData = new BriefingData();
        briefingData.title = title;
        briefingData.date = dateStr;
        briefingData.articleId = articleId;

        StringBuilder contentSb = new StringBuilder();
        contentSb.append("早上好，现在为您播报财联社有声早报。");
        contentSb.append("今天是").append(dateStr).append("。");
        contentSb.append(title).append("。");
        contentSb.append("以下是详细内容：");
        contentSb.append(formatBrief(brief));
        contentSb.append("以上就是今日早报的主要内容，祝您一天愉快。");
        briefingData.content = contentSb.toString();

        if (!articleId.isEmpty()) {
            try {
                String articleUrl = "https://www.cls.cn/detail/" + articleId;
                String articleHtml = fetchHtml(articleUrl, subjectUrl);
                JSONObject articleNextData = extractNextData(articleHtml);

                if (articleNextData != null) {
                    JSONObject articleDetail = articleNextData.optJSONObject("props")
                            .optJSONObject("pageProps")
                            .optJSONObject("articleDetail");

                    if (articleDetail != null) {
                        String audioUrl = articleDetail.optString("audioUrl", "");
                        if (audioUrl == null || audioUrl.isEmpty()) {
                            audioUrl = articleDetail.optString("miniMaxAudioUrl", "");
                        }
                        briefingData.audioUrl = audioUrl;

                        String fullContent = articleDetail.optString("content", "");
                        if (fullContent != null && !fullContent.isEmpty()) {
                            String textContent = htmlToText(fullContent);
                            if (textContent.length() > 100) {
                                briefingData.content = "早上好，现在为您播报财联社有声早报。" +
                                        "今天是" + dateStr + "。" +
                                        textContent +
                                        "以上就是今日早报的主要内容，祝您一天愉快。";
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return briefingData;
    }

    private String fetchHtml(String urlStr, String referer) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Referer", referer);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("HTTP " + responseCode);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        conn.disconnect();

        return sb.toString();
    }

    private JSONObject extractNextData(String html) throws Exception {
        Pattern pattern = Pattern.compile("<script[^>]*id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String jsonStr = matcher.group(1);
            return new JSONObject(jsonStr);
        }
        return null;
    }

    private String formatBrief(String brief) {
        if (brief == null || brief.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String[] items = brief.split("①|②|③|④|⑤|⑥|⑦|⑧|⑨|⑩");
        int num = 1;
        for (String item : items) {
            item = item.trim();
            if (!item.isEmpty()) {
                result.append("第").append(num).append("条，").append(item).append("。");
                num++;
            }
        }
        return result.toString();
    }

    private String htmlToText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = html.replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<p[^>]*>", "\n")
                .replaceAll("</p>", "")
                .replaceAll("<strong[^>]*>", "")
                .replaceAll("</strong>", "")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        return text;
    }
}
