package com.cui.edu.common;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 分页返回结果
 */
@Data
public class PageResult {
	/**
	 * 当前页码
	 */
	private long pageNum;
	/**
	 * 每页数量
	 */
	private long pageSize;
	/**
	 * 记录总数
	 */
	private long totalSize;
	/**
	 * 页码总数
	 */
	private long totalPages;
	/**
	 * 分页数据
	 */
	private List<?> content;
	/**
	 * 除去分页数据外，也需要返回给前端的数据
	 */
	private Map map;
}
