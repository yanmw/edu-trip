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
	 * 银联-等待买家支付
	 */
	String WAIT_BUYER_PAY = "WAIT_BUYER_PAY";
	/**
	 * 银联-交易关闭
	 */
	String TRADE_CLOSED = "TRADE_CLOSED";
	/**
	 * 银联-新订单
	 */
	String NEW_ORDER = "NEW_ORDER";
	/**
	 * 银联-不明确的交易状态
	 */
	String UNKNOWN = "UNKNOWN";
	/**
	 * 银联-退款
	 */
	String TRADE_REFUND = "TRADE_REFUND";
	/**
	 * 银联-退款成功
	 */
	String REFUND_SUCCESS = "SUCCESS";
	/**
	 * 银联-退款失败
	 */
	String REFUND_FAIL = "FAIL";
	/**
	 * 银联-退款处理中
	 */
	String REFUND_PROCESSING = "PROCESSING";
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
