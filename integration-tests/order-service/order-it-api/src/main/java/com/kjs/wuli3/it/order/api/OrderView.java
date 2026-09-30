package com.kjs.wuli3.it.order.api;

/**
 * 面向 HTTP 调用方的订单快照。
 *
 * @author 国杨 create on 2026/9/30 17:30
 */
public record OrderView(String id, String status) {}
