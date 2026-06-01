package com.cui.edu.common;

/**
 * 常量管理
 */
public interface SysConstants {

	/**
	 * 是
	 */
	Integer IS_TRUE = 1;

	/**
	 * 否
	 */
	Integer IS_FALSE = 0;

	/**
	 * 异常
	 */
	String NULL_EXCEPTION = "空指针异常";
	String EXCEPTION = "异常";
	String IO_EXCEPTION = "IO异常";
	String FORBIDDEN = "无权限异常";

	/**
	 * msg：提示信息
	 */
	String MSG = "msg";

	/**
	 * redis key前缀
	 */
	String SET_NX = "setNx:";

	/**
	 * 银联-支付成功
	 */
	String TRADE_SUCCESS = "TRADE_SUCCESS";
	/**
	 * 银联-退款
	 */
	String TRADE_REFUND = "TRADE_REFUND";
	/**
	 * errCode：
	 * 退款失败-头寸不足
	 */
	String POSITION_LACK = "POSITION_LACK";
	/**
	 * errCode：
	 * 操作成功
	 */
	String SUCCESS = "SUCCESS";
}
