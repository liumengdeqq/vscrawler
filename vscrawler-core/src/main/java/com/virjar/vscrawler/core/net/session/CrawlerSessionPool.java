package com.virjar.vscrawler.core.net.session;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.Lists;
import com.virjar.vscrawler.core.event.support.AutoEventRegistry;
import com.virjar.vscrawler.core.event.systemevent.CrawlerEndEvent;
import com.virjar.vscrawler.core.event.systemevent.SessionBorrowEvent;
import com.virjar.vscrawler.core.event.systemevent.SessionCreateEvent;
import com.virjar.vscrawler.core.net.CrawlerHttpClientGenerator;
import com.virjar.vscrawler.core.net.proxy.IPPool;
import com.virjar.vscrawler.core.net.proxy.strategy.ProxyPlanner;
import com.virjar.vscrawler.core.net.proxy.strategy.ProxyStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 17/4/15.<br/>
 * 创建并管理多个用户的链接,pool逻辑大概是模仿druid实现的
 * 
 * @author virjar
 * @since 0.0.1
 */
@Slf4j
public class CrawlerSessionPool implements CrawlerEndEvent {

    private LinkedBlockingQueue<CrawlerSession> allSessions = new LinkedBlockingQueue<>();

    private int maxSize = 10;

    private int coreSize = 0;

    private int initialSize = 0;

    private long reuseDuration = 60 * 60 * 1000;

    private long maxOnlineDuration = Long.MAX_VALUE;

    private CrawlerHttpClientGenerator crawlerHttpClientGenerator;

    /**
     * 代理切换策略
     */
    private ProxyStrategy proxyStrategy;

    private IPPool ipPool;
    private ProxyPlanner proxyPlanner = null;

    // 是否初始化
    protected volatile boolean inited = false;

    private ReentrantLock lock = new ReentrantLock();
    protected Condition empty = lock.newCondition();

    private CreateSessionThread createSessionThread;

    public CrawlerSessionPool(CrawlerHttpClientGenerator crawlerHttpClientGenerator, ProxyStrategy proxyStrategy,
            IPPool ipPool, ProxyPlanner proxyPlanner, int maxSize, int coreSize, int initialSize, long reuseDuration,
            long maxOnlineDuration) {
        this.crawlerHttpClientGenerator = crawlerHttpClientGenerator;
        this.proxyStrategy = proxyStrategy;
        this.ipPool = ipPool;
        this.proxyPlanner = proxyPlanner;
        this.maxSize = maxSize;
        this.coreSize = coreSize;
        this.initialSize = initialSize;
        this.reuseDuration = reuseDuration;
        this.maxOnlineDuration = maxOnlineDuration;
    }

    public void init() {
        if (inited) {
            return;
        }
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {

            if (inited) {
                return;
            }

            if (maxSize < coreSize) {
                throw new IllegalArgumentException("maxSize " + maxSize + "  must grater than coreSize " + coreSize);
            }
            if (initialSize > maxSize) {
                throw new IllegalArgumentException(
                        "maxSize " + maxSize + "  must grater than initialSize " + initialSize);
            }

            if (reuseDuration < 0) {
                reuseDuration = 0;
            }

            if (initialSize > 0) {
                int totalTry = 0;
                for (int i = 0; i < initialSize; i++) {
                    totalTry++;
                    CrawlerSession newSession = createNewSession();
                    if (newSession == null) {
                        i--;
                    } else {
                        allSessions.add(newSession);
                    }
                    if (totalTry > initialSize * 3) {
                        throw new IllegalStateException("can not create session ,all session create failed");
                    }
                }
            }

            createSessionThread = new CreateSessionThread();
            createSessionThread.start();
        } finally {
            inited = true;
            lock.unlock();
        }
    }

    private CrawlerSession createNewSession() {
        CrawlerSession crawlerSession = new CrawlerSession(crawlerHttpClientGenerator, proxyStrategy, ipPool,
                proxyPlanner, this);
        AutoEventRegistry.getInstance().findEventDeclaring(SessionCreateEvent.class)
                .onSessionCreateEvent(crawlerSession);
        if (crawlerSession.isValid()) {
            crawlerSession.setInitTimeStamp(System.currentTimeMillis());
            return crawlerSession;
        }
        return null;
    }

    public void recycle(CrawlerSession crawlerSession) {
        if (allSessions.size() > maxSize || !crawlerSession.isValid()) {
            crawlerSession.destroy();
        } else {
            allSessions.add(crawlerSession);
        }
    }

    public CrawlerSession borrowOne(long maxWaitMillis) {
        init();
        long lessTimeMillis = maxWaitMillis;
        LinkedList<CrawlerSession> tempCrawlerSession = Lists.newLinkedList();

        // logger.info("当前会话池中,共有:{}个用户可用", allSessions.size());
        try {
            for (;;) {
                long startRequestTimeStamp = System.currentTimeMillis();
                CrawlerSession crawlerSession = getSessionInternal(lessTimeMillis);
                if (crawlerSession == null) {// 如果系统本身线程数不够,则使用主调线程,此方案后续讨论是否合适
                    crawlerSession = createNewSession();
                }
                if (crawlerSession == null && lessTimeMillis < 0 && maxWaitMillis > 0) {
                    return null;
                }
                lessTimeMillis = lessTimeMillis - (System.currentTimeMillis() - startRequestTimeStamp);
                if (crawlerSession == null) {
                    continue;
                }

                // 各种check
                if (crawlerSession.isValid()) {
                    crawlerSession.destroy();
                    continue;
                }

                // 单个session使用太频繁
                if (System.currentTimeMillis() - crawlerSession.getLastActiveTimeStamp() < reuseDuration) {
                    tempCrawlerSession.add(crawlerSession);
                    continue;
                }

                // 单个session使用太久了
                if (System.currentTimeMillis() - crawlerSession.getInitTimeStamp() > maxOnlineDuration) {
                    crawlerSession.destroy();
                    continue;
                }

                AutoEventRegistry.getInstance().findEventDeclaring(SessionBorrowEvent.class)
                        .onSessionBorrow(crawlerSession);
                return crawlerSession;
            }
        } finally {
            allSessions.addAll(tempCrawlerSession);
        }
    }

    private CrawlerSession getSessionInternal(long maxWait) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new PoolException("lock interrupted", e);
        }

        try {
            if (allSessions.size() < coreSize) {
                empty.signal();
            }
            if (maxWait > 0) {
                return allSessions.poll(maxWait, TimeUnit.MILLISECONDS);
            } else {
                return allSessions.poll();
            }
        } catch (InterruptedException interruptedException) {
            throw new PoolException("lock interrupted", interruptedException);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void crawlerEnd() {
        createSessionThread.interrupt();
    }

    private class CreateSessionThread extends Thread {
        CreateSessionThread() {
            super("createNewSession");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    empty.await();
                } catch (InterruptedException e) {
                    log.warn("wait interrupted", e);
                    break;
                }
                int expected = coreSize - allSessions.size();

                for (int i = 0; i < expected * 2; i++) {
                    CrawlerSession newSession = createNewSession();
                    if (newSession != null) {
                        allSessions.add(newSession);
                    }
                    if (allSessions.size() >= coreSize) {
                        break;
                    }
                }
                if (allSessions.size() < coreSize) {
                    log.warn("many of sessions create failed,please check  your config");
                }
            }
        }
    }
}
