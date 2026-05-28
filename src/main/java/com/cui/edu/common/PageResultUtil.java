package com.cui.edu.common;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * MyBatis 分页查询助手
 */
public class PageResultUtil {

	/**
	 * 将分页信息封装到统一的接口
	 * @param
	 * @param
	 * @return
	 */
	public static PageResult getPageResult(Page<?> pageInfo, List<?>list) {
		PageResult pageResult = new PageResult();
        pageResult.setPageNum(pageInfo.getCurrent());
        pageResult.setPageSize(pageInfo.getSize());
        pageResult.setTotalSize(pageInfo.getTotal());
        pageResult.setTotalPages(pageInfo.getPages());
        pageResult.setContent(list);
		return pageResult;
	}
    public static PageResult getPageResult(Page<?> pageInfo, List<?>list, Map map) {
        PageResult pageResult = new PageResult();
        pageResult.setPageNum(pageInfo.getCurrent());
        pageResult.setPageSize(pageInfo.getSize());
        pageResult.setTotalSize(pageInfo.getTotal());
        pageResult.setTotalPages(pageInfo.getPages());
        pageResult.setContent(list);
        pageResult.setMap(map);
        return pageResult;
    }

	public static PageResult getPageResult(Page<?> pageInfo) {
        PageResult pageResult = new PageResult();
        pageResult.setPageNum(pageInfo.getCurrent());
        pageResult.setPageSize(pageInfo.getSize());
        pageResult.setTotalSize(pageInfo.getTotal());
        pageResult.setTotalPages(pageInfo.getPages());
        pageResult.setContent(pageInfo.getRecords());
        return pageResult;
    }

    public static PageResult getPageResult(Page<?> pageInfo, Map map) {
        PageResult pageResult = new PageResult();
        pageResult.setPageNum(pageInfo.getCurrent());
        pageResult.setPageSize(pageInfo.getSize());
        pageResult.setTotalSize(pageInfo.getTotal());
        pageResult.setTotalPages(pageInfo.getPages());
        pageResult.setContent(pageInfo.getRecords());
        pageResult.setMap(map);
        return pageResult;
    }

}
