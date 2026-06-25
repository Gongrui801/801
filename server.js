const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const cors = require('cors');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

const clsHeaders = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
  'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
  'Referer': 'https://www.cls.cn/'
};

app.get('/api/morning-briefing', async (req, res) => {
  try {
    const date = req.query.date || new Date().toISOString().split('T')[0];
    
    const briefingData = await fetchMorningBriefing(date);
    
    if (briefingData) {
      res.json({
        success: true,
        data: briefingData
      });
    } else {
      res.json({
        success: false,
        message: '未获取到当日早报内容',
        data: getFallbackBriefing()
      });
    }
  } catch (error) {
    console.error('获取早报失败:', error.message);
    res.json({
      success: false,
      message: '获取早报失败: ' + error.message,
      data: getFallbackBriefing()
    });
  }
});

async function fetchMorningBriefing(date) {
  try {
    const telegrams = await fetchTelegrams();
    const morningNews = telegrams.filter(item => {
      const itemDate = new Date(item.ctime * 1000).toISOString().split('T')[0];
      return itemDate === date;
    });
    
    if (morningNews.length > 0) {
      return {
        title: `财联社早报 ${date}`,
        date: date,
        source: '财联社',
        items: morningNews.slice(0, 20).map(item => ({
          title: item.title || item.brief,
          content: item.content || item.brief || item.title,
          time: formatTime(item.ctime)
        }))
      };
    }
    
    return null;
  } catch (error) {
    console.error('抓取财联社数据失败:', error);
    return null;
  }
}

async function fetchTelegrams() {
  try {
    const url = 'https://www.cls.cn/nodeapi/telegraphList';
    const response = await axios.get(url, {
      headers: clsHeaders,
      params: {
        app: 'CailianpressWeb',
        os: 'web',
        sv: '8.4.6',
        sign: ''
      },
      timeout: 10000
    });
    
    if (response.data && response.data.data && response.data.data.roll_data) {
      return response.data.data.roll_data;
    }
    
    return [];
  } catch (error) {
    console.error('获取电报列表失败:', error.message);
    return [];
  }
}

function formatTime(timestamp) {
  const date = new Date(timestamp * 1000);
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function getFallbackBriefing() {
  const today = new Date().toISOString().split('T')[0];
  return {
    title: `财经早报 ${today}`,
    date: today,
    source: '备用数据源',
    items: [
      {
        title: '市场概述',
        content: '昨日A股市场整体震荡运行，沪指小幅收涨，创业板指表现相对较弱。成交量较前一交易日有所萎缩，市场观望情绪较浓。',
        time: '07:00'
      },
      {
        title: '宏观经济',
        content: '国家统计局发布最新经济数据显示，国内经济保持稳步复苏态势，主要经济指标持续改善。',
        time: '07:05'
      },
      {
        title: '行业动态',
        content: '新能源汽车产业持续向好，多家车企公布上月销量数据，同比均实现大幅增长。',
        time: '07:10'
      },
      {
        title: '公司要闻',
        content: '多家上市公司发布重要公告，包括业绩预告、重大合同签订等事项，投资者需关注相关影响。',
        time: '07:15'
      },
      {
        title: '海外市场',
        content: '美股昨夜涨跌互现，科技股表现分化，市场关注美联储货币政策走向及经济数据。',
        time: '07:20'
      }
    ]
  };
}

app.listen(PORT, () => {
  console.log(`财联社早报闹钟服务已启动: http://localhost:${PORT}`);
});
