package com.virjar.vscrawler.core.net;

import com.virjar.dungproxy.client.httpclient.CrawlerHttpClient;
import com.virjar.dungproxy.client.httpclient.CrawlerHttpClientBuilder;

/**
 * Created by virjar on 17/4/30.
 * 
 * @author virjar
 * @since 0.0.1
 */
public interface CrawlerHttpClientGenerator {
    /**
     * 构建一个httpclient,请注意必须使用ProxyFeedBackDecorateHttpClientBuilder构造httpclient,否则代理自动打分功能不会生效
     * ProxyFeedBackDecorateHttpClientBuilder产生的httpclient会自动拦截请求动作,并根据异常情况对IP进行打分
     *
     * @param crawlerHttpClientBuilder httpclient构建器
     * @return 适合爬虫的httpclient对象
     */
    CrawlerHttpClient gen(CrawlerHttpClientBuilder crawlerHttpClientBuilder);
}
