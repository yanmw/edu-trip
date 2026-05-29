package com.cui.edu.trip.service.impl;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cui.edu.config.exception.MyException;
import com.cui.edu.common.HttpStatus;
import com.cui.edu.common.PageResult;
import com.cui.edu.common.PageResultUtil;
import com.cui.edu.common.SysConstants;
import com.cui.edu.trip.entity.Visitor;
import com.cui.edu.trip.mapper.VisitorMapper;
import com.cui.edu.trip.service.VisitorService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cui.edu.vo.trip.VisitorVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * 游客表 服务实现类
 * </p>
 *
 * @author Cuicui
 * @since 2026-05-29
 */
@Service
public class VisitorServiceImpl extends ServiceImpl<VisitorMapper, Visitor> implements VisitorService {

    // 身份证号码前两位对应省级行政区。
    private static final Map<String, String> PROVINCE_MAP = new HashMap<>();

    private static final List<String> IMPORT_TEMPLATE_HEADERS = Arrays.asList("姓名", "手机号", "身份证号");

    private static final Set<String> IMPORT_REQUIRED_FIELDS = new HashSet<>(Arrays.asList("name", "mobile", "idCard"));

    static {
        PROVINCE_MAP.put("11", "北京市");
        PROVINCE_MAP.put("12", "天津市");
        PROVINCE_MAP.put("13", "河北省");
        PROVINCE_MAP.put("14", "山西省");
        PROVINCE_MAP.put("15", "内蒙古自治区");
        PROVINCE_MAP.put("21", "辽宁省");
        PROVINCE_MAP.put("22", "吉林省");
        PROVINCE_MAP.put("23", "黑龙江省");
        PROVINCE_MAP.put("31", "上海市");
        PROVINCE_MAP.put("32", "江苏省");
        PROVINCE_MAP.put("33", "浙江省");
        PROVINCE_MAP.put("34", "安徽省");
        PROVINCE_MAP.put("35", "福建省");
        PROVINCE_MAP.put("36", "江西省");
        PROVINCE_MAP.put("37", "山东省");
        PROVINCE_MAP.put("41", "河南省");
        PROVINCE_MAP.put("42", "湖北省");
        PROVINCE_MAP.put("43", "湖南省");
        PROVINCE_MAP.put("44", "广东省");
        PROVINCE_MAP.put("45", "广西壮族自治区");
        PROVINCE_MAP.put("46", "海南省");
        PROVINCE_MAP.put("50", "重庆市");
        PROVINCE_MAP.put("51", "四川省");
        PROVINCE_MAP.put("52", "贵州省");
        PROVINCE_MAP.put("53", "云南省");
        PROVINCE_MAP.put("54", "西藏自治区");
        PROVINCE_MAP.put("61", "陕西省");
        PROVINCE_MAP.put("62", "甘肃省");
        PROVINCE_MAP.put("63", "青海省");
        PROVINCE_MAP.put("64", "宁夏回族自治区");
        PROVINCE_MAP.put("65", "新疆维吾尔自治区");
        PROVINCE_MAP.put("71", "台湾省");
        PROVINCE_MAP.put("81", "香港特别行政区");
        PROVINCE_MAP.put("82", "澳门特别行政区");
    }

    @Override
    public void saveVisitor(Visitor record) {
        // 保存游客时自动根据身份证补齐省份和性别。
        fillProvinceAndGender(record);
        if (record.getId() == null && record.getIsDeleted() == null) {
            record.setIsDeleted(SysConstants.IS_FALSE);
        }
        super.saveOrUpdate(record);
    }

    @Override
    public int importExcel(MultipartFile file, Long teamId) {
        if (file == null || file.isEmpty()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "导入文件不能为空");
        }

        try (ExcelReader reader = ExcelUtil.getReader(file.getInputStream())) {
            validateImportHeaders(reader.readRow(0));
            addHeaderAlias(reader);
            List<Visitor> visitorList = reader.readAll(Visitor.class);
            if (visitorList == null || visitorList.isEmpty()) {
                return 0;
            }
            for (Visitor visitor : visitorList) {
                visitor.setId(null);
                visitor.setTeamId(teamId);
                visitor.setIsDeleted(SysConstants.IS_FALSE);
                fillProvinceAndGender(visitor);
            }
            super.saveBatch(visitorList);
            return visitorList.size();
        } catch (IOException e) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel导入失败");
        }
    }

    @Override
    public byte[] getImportTemplate() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ExcelWriter writer = ExcelUtil.getWriter(true)) {
            writer.writeHeadRow(IMPORT_TEMPLATE_HEADERS);
            writer.autoSizeColumnAll();
            writer.flush(outputStream, true);
        }
        return outputStream.toByteArray();
    }

    @Override
    public PageResult findPage(VisitorVO vo) {
        Page<Visitor> page = new Page<>(vo.getPageNum(), vo.getPageSize());
        QueryWrapper<Visitor> ew = new QueryWrapper<>();
        if (StringUtils.isNotBlank(vo.getName())) {
            ew.like(Visitor.NAME, vo.getName());
        }
        if (StringUtils.isNotBlank(vo.getMobile())) {
            ew.like(Visitor.MOBILE, vo.getMobile());
        }
        if (StringUtils.isNotBlank(vo.getIdCard())) {
            ew.like(Visitor.ID_CARD, vo.getIdCard());
        }
        if (StringUtils.isNotBlank(vo.getWechatOpenid())) {
            ew.eq(Visitor.WECHAT_OPENID, vo.getWechatOpenid());
        }
        if (StringUtils.isNotBlank(vo.getProvince())) {
            ew.eq(Visitor.PROVINCE, vo.getProvince());
        }
        if (vo.getGender() != null) {
            ew.eq(Visitor.GENDER, vo.getGender());
        }
        if (vo.getTeamId() != null) {
            ew.eq(Visitor.TEAM_ID, vo.getTeamId());
        }
        ew.eq(Visitor.IS_DELETED, SysConstants.IS_FALSE);
        ew.orderByDesc(Visitor.ID);
        page = super.page(page, ew);
        return PageResultUtil.getPageResult(page);
    }

    @Override
    public void logicDelete(List<Long> ids) {
        List<Visitor> visitorList = new ArrayList<>();
        for (Long id : ids) {
            Visitor visitor = new Visitor();
            visitor.setId(id);
            visitor.setIsDeleted(SysConstants.IS_TRUE);
            visitorList.add(visitor);
        }
        super.updateBatchById(visitorList);
    }

    private void fillProvinceAndGender(Visitor record) {
        String idCard = record.getIdCard();
        if (StringUtils.isBlank(idCard)) {
            return;
        }
        if (idCard.length() >= 2) {
            record.setProvince(PROVINCE_MAP.get(idCard.substring(0, 2)));
        }
        if (idCard.length() == 18) {
            // 18位身份证第17位为性别顺序码，奇数男、偶数女。
            setGender(record, idCard.charAt(16));
        } else if (idCard.length() == 15) {
            // 15位身份证第15位为性别顺序码。
            setGender(record, idCard.charAt(14));
        }
    }

    private void setGender(Visitor record, char genderCode) {
        if (genderCode >= '0' && genderCode <= '9') {
            record.setGender((genderCode - '0') % 2 == 1 ? SysConstants.IS_TRUE : SysConstants.IS_FALSE);
        }
    }

    private void addHeaderAlias(ExcelReader reader) {
        reader.addHeaderAlias("姓名", "name");
        reader.addHeaderAlias("name", "name");
        reader.addHeaderAlias("手机号", "mobile");
        reader.addHeaderAlias("mobile", "mobile");
        reader.addHeaderAlias("身份证号", "idCard");
        reader.addHeaderAlias("身份证", "idCard");
        reader.addHeaderAlias("idCard", "idCard");
    }

    private void validateImportHeaders(List<Object> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel表头不能为空");
        }

        int headerCount = 0;
        Set<String> actualFields = new LinkedHashSet<>();
        for (Object header : headers) {
            if (header == null || StringUtils.isBlank(header.toString())) {
                continue;
            }
            headerCount++;
            String fieldName = convertHeaderToFieldName(header.toString().trim());
            if (fieldName == null) {
                throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel只能包含手机号、身份证号、姓名");
            }
            actualFields.add(fieldName);
        }

        if (headerCount != IMPORT_REQUIRED_FIELDS.size() || !IMPORT_REQUIRED_FIELDS.equals(actualFields)) {
            throw new MyException(HttpStatus.SC_BAD_REQUEST, "Excel必须且只能包含手机号、身份证号、姓名");
        }
    }

    private String convertHeaderToFieldName(String header) {
        if ("手机号".equals(header) || "mobile".equals(header)) {
            return "mobile";
        }
        if ("身份证号".equals(header) || "身份证".equals(header) || "idCard".equals(header)) {
            return "idCard";
        }
        if ("姓名".equals(header) || "name".equals(header)) {
            return "name";
        }
        return null;
    }
}
