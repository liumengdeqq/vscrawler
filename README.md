#vscrawler

vscrawler是一个更加适合抓取的爬虫框架，他不是教科书似的爬虫，准确说他不是爬虫，没有广度优先遍历这些说法，他所面临的网站URL不是网络里面的网络拓扑图而是一个个目标明确的抓取任务。

vscrawler的一个重要特性就是他把下载和解析放在了同一个组件里面，同时他天生支持多用户登录。vscrawler设计的目的是填补webmagic在某些方面的不足，不过vscrawler本身很多思想也参考webmagic，感谢webmagic作者黄大大。


编写vscrawler的契机是本人在抓取企信宝的时候遇到的滑块验证码突破问题，多用户登录问题，复杂流程抽取问题。同时他基于dungproxy作为网络层API，天生接入了代理服务。vscrawler目前还是我花不到两天弄出来的小框架，可能有各种不完善的地方，不过一定会越来越完善的，起来越来越好的明天