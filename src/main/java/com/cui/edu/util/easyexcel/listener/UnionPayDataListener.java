package com.cui.edu.util.easyexcel.listener;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.util.ListUtils;

import com.cui.edu.system.entity.Museum;
import com.cui.edu.system.service.MuseumService;
import com.cui.edu.trip.entity.unionpay.UnionPayData;
import com.cui.edu.trip.service.UnionPayDataService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class UnionPayDataListener extends AnalysisEventListener<UnionPayData> {
    private static final int BATCH_COUNT = 1000;

    private List<UnionPayData> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

    private UnionPayDataService unionPayDataService;

    private MuseumService museumService;

    private List<Museum> museumList;

    private List<String> mid = new ArrayList<>();
    private List<String> tid = new ArrayList<>();

    public UnionPayDataListener(UnionPayDataService unionPayDataService, MuseumService museumService) {
        this.unionPayDataService = unionPayDataService;
        this.museumService = museumService;
    }

    @Override
    public void invoke(UnionPayData unionPayData, AnalysisContext analysisContext) {
        // 只解析自助租赁的数据
        if (ObjectUtil.isEmpty(museumList)) {
            museumList = museumService.list();
            List<String> midList = museumList.stream().map(Museum::getMid).filter(ObjectUtil::isNotEmpty).collect(Collectors.toList());
            List<String> tidList = museumList.stream().map(Museum::getTid).filter(ObjectUtil::isNotEmpty).collect(Collectors.toList());
            mid.addAll(midList);
            tid.addAll(tidList);
        }
        if (mid.contains(unionPayData.getMid()) && tid.contains(unionPayData.getTid())) {
            cachedDataList.add(unionPayData);
            // 达到BATCH_COUNT了，需要去存储一次数据库，防止数据几万条数据在内存，容易OOM
            if (cachedDataList.size() >= BATCH_COUNT) {
                saveData();
                // 存储完成清理 list
                cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
            }
        }
    }

    /**
     * 所有数据解析完成了 都会来调用
     *
     * @param analysisContext
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        // 这里也要保存数据，确保最后遗留的数据也存储到数据库
        saveData();
        log.info("所有数据解析完成！");
    }

    /**
     * 加上存储数据库
     */
    private void saveData() {
        if (ObjectUtil.isEmpty(cachedDataList)) {
            return;
        }
        log.info("{}条数据，开始存储数据库！", cachedDataList.size());
        unionPayDataService.saveBatch(cachedDataList);
        log.info("存储数据库成功！");
    }
}
