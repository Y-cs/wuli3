package com.kjs.wuli3.spring.context;

/**
 * 声明 Servlet 自行管理运行上下文的容器注册契约。
 *
 * 注意：仅通过服务端 Servlet 注册初始化参数声明所有权，不接受请求头、请求参数等客户端输入。
 * Web 上下文过滤器据此避免重复建立作用域，独立传输适配负责绑定与还原自身上下文。
 *
 * @author 国杨 create on 2026/11/5 15:00
 */
public final class ServletContextOwnership {
    /** Servlet 注册中声明上下文所有权的初始化参数名。 */
    public static final String INIT_PARAMETER = "wuli3.context-owner";

    /** 表示由目标 Servlet 负责上下文的完整生命周期。 */
    public static final String SERVLET = "servlet";

    private ServletContextOwnership() {}
}
