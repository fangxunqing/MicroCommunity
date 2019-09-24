package com.java110.web.smo.assetImport.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.java110.common.constant.ServiceConstant;
import com.java110.common.util.Assert;
import com.java110.common.util.ImportExcelUtils;
import com.java110.core.context.IPageData;
import com.java110.entity.assetImport.ImportFloor;
import com.java110.entity.assetImport.ImportOwner;
import com.java110.entity.assetImport.ImportParkingSpace;
import com.java110.entity.assetImport.ImportRoom;
import com.java110.entity.component.ComponentValidateResult;
import com.java110.web.core.BaseComponentSMO;
import com.java110.web.smo.assetImport.IAssetImportSMO;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName AssetImportSmoImpl
 * @Description TODO
 * @Author wuxw
 * @Date 2019/9/23 23:14
 * @Version 1.0
 * add by wuxw 2019/9/23
 **/
@Service("assetImportSMOImpl")
public class AssetImportSMOImpl extends BaseComponentSMO implements IAssetImportSMO {
    private final static Logger logger = LoggerFactory.getLogger(AssetImportSMOImpl.class);

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ResponseEntity<String> importExcelData(IPageData pd, MultipartFile uploadFile) throws Exception {

        ComponentValidateResult result = this.validateStoreStaffCommunityRelationship(pd, restTemplate);

        //InputStream is = uploadFile.getInputStream();

        Workbook workbook = null;  //工作簿
        //工作表
        String[] headers = null;   //表头信息
        List<ImportFloor> floors = new ArrayList<ImportFloor>();
        List<ImportOwner> owners = new ArrayList<ImportOwner>();
        List<ImportRoom> rooms = new ArrayList<ImportRoom>();
        List<ImportParkingSpace> parkingSpaces = new ArrayList<ImportParkingSpace>();
        workbook = ImportExcelUtils.createWorkbook(uploadFile);
        //获取楼信息
        getFloors(workbook, floors);
        //获取业主信息
        getOwners(workbook, owners);

        //获取房屋信息
        getRooms(workbook, rooms, floors, owners);

        //获取车位信息
        getParkingSpaces(workbook, parkingSpaces, owners);

        //数据校验
        importExcelDataValidate(floors, owners, rooms, parkingSpaces);

        // 保存数据
        return dealExcelData(pd, floors, owners, rooms, parkingSpaces, result);
    }

    /**
     * 处理ExcelData数据
     *
     * @param floors        楼栋单元信息
     * @param owners        业主信息
     * @param rooms         房屋信息
     * @param parkingSpaces 车位信息
     */
    private ResponseEntity<String> dealExcelData(IPageData pd, List<ImportFloor> floors, List<ImportOwner> owners, List<ImportRoom> rooms, List<ImportParkingSpace> parkingSpaces, ComponentValidateResult result) {
        ResponseEntity<String> responseEntity = null;
        //保存单元信息 和 楼栋信息
        responseEntity = savedFloorAndUnitInfo(pd, floors, result);

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }

        // 保存业主信息
        responseEntity = savedOwnerInfo(pd, owners, result);
        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            return responseEntity;
        }

        //保存房屋
        responseEntity = savedRoomInfo(pd,rooms,result);
        return responseEntity;
    }

    /**
     * 保存房屋信息
     * @param pd
     * @param rooms
     * @param result
     * @return
     */
    private ResponseEntity<String> savedRoomInfo(IPageData pd, List<ImportRoom> rooms, ComponentValidateResult result) {
        String apiUrl = "";
        JSONObject paramIn = null;
        ResponseEntity<String> responseEntity = null;
        for(ImportRoom room : rooms){
            JSONObject savedRoomInfo = getExistsRoom(pd, result, room);

            if (savedRoomInfo != null) {
                continue;
            }
        }

        return responseEntity;
    }

    /**
     * 查询存在的房屋信息
     * room.queryRooms
     *
     * @param pd
     * @param result
     * @param room
     * @return
     */
    private JSONObject getExistsRoom(IPageData pd, ComponentValidateResult result, ImportRoom room) {
        String apiUrl = "";
        ResponseEntity<String> responseEntity = null;
        apiUrl = ServiceConstant.SERVICE_API_URL + "/api/room.queryRooms?communityId=" + result.getCommunityId()
                + "&floorId=" + room.getFloor().getFloorId() + "&unitId=" + room.getFloor().getUnitId()+"&roomNum="+room.getRoomNum();
        responseEntity = this.callCenterService(restTemplate, pd, "", apiUrl, HttpMethod.GET);

        if (responseEntity.getStatusCode() != HttpStatus.OK) { //跳过 保存单元信息
            return null;
        }

        JSONObject savedRoomInfoResults = JSONObject.parseObject(responseEntity.getBody());


        if (!savedRoomInfoResults.containsKey("rooms") || savedRoomInfoResults.getJSONArray("rooms").size() != 1) {
            return null;
        }


        JSONObject savedRoomInfo = savedRoomInfoResults.getJSONArray("rooms").getJSONObject(0);

        return savedRoomInfo;
    }

    /**
     * 保存业主信息
     *
     * @param pd
     * @param owners
     * @param result
     * @return
     */
    private ResponseEntity<String> savedOwnerInfo(IPageData pd, List<ImportOwner> owners, ComponentValidateResult result) {
        String apiUrl = "";
        JSONObject paramIn = null;
        ResponseEntity<String> responseEntity = null;

        for (ImportOwner owner : owners) {
            JSONObject savedOwnerInfo = getExistsOwner(pd, result, owner);

            if (savedOwnerInfo != null) {
                continue;
            }
            paramIn = new JSONObject();

            apiUrl = ServiceConstant.SERVICE_API_URL + "/api/owner.saveOwner";

            paramIn.put("communityId", result.getCommunityId());
            paramIn.put("userId", result.getUserId());
            paramIn.put("name", owner.getOwnerName());
            paramIn.put("age", owner.getAge());
            paramIn.put("link", owner.getTel());
            paramIn.put("sex", owner.getSex());
            paramIn.put("ownerTypeCd", "1001");
            responseEntity = this.callCenterService(restTemplate, pd, paramIn.toJSONString(), apiUrl, HttpMethod.POST);
            if (responseEntity.getStatusCode() != HttpStatus.OK) {
                savedOwnerInfo = getExistsOwner(pd, result, owner);
                owner.setOwnerId(savedOwnerInfo.getString("ownerId"));
            }
        }

        return responseEntity;
    }

    /**
     * 保存 楼栋和 单元信息
     *
     * @param pd
     * @param floors
     * @param result
     * @return
     */
    private ResponseEntity<String> savedFloorAndUnitInfo(IPageData pd, List<ImportFloor> floors, ComponentValidateResult result) {
        String apiUrl = "";
        JSONObject paramIn = null;
        ResponseEntity<String> responseEntity = null;
        for (ImportFloor importFloor : floors) {
            paramIn = new JSONObject();
            //先保存 楼栋信息
            JSONObject savedFloorInfo = getExistsFloor(pd, result, importFloor);
            // 如果不存在，才插入
            if (savedFloorInfo == null) {
                apiUrl = ServiceConstant.SERVICE_API_URL + "/api/floor.saveFloor";
                paramIn.put("communityId", result.getCommunityId());
                paramIn.put("floorNum", importFloor.getFloorNum());
                paramIn.put("userId", result.getUserId());
                paramIn.put("name", importFloor.getFloorNum() + "号楼");
                responseEntity = this.callCenterService(restTemplate, pd, paramIn.toJSONString(), apiUrl, HttpMethod.POST);
            }
            if (responseEntity.getStatusCode() != HttpStatus.OK) { //跳过 保存单元信息
                continue;
            }

            savedFloorInfo = getExistsFloor(pd, result, importFloor);

            if (savedFloorInfo == null) {
                continue;
            }
            importFloor.setFloorId(savedFloorInfo.getString("floorId"));
            paramIn.clear();
            //判断单元信息是否已经存在，如果存在则不保存数据unit.queryUnits
            if (getExistsUnit(pd, result, importFloor) != null) {
                continue;
            }

            apiUrl = ServiceConstant.SERVICE_API_URL + "/api/unit.saveUnit";

            paramIn.put("communityId", result.getCommunityId());
            paramIn.put("floorId", savedFloorInfo.getString("floorId"));
            paramIn.put("unitNum", importFloor.getUnitNum());
            paramIn.put("layerCount", importFloor.getLayerCount());
            paramIn.put("lift", importFloor.getLift());
            responseEntity = this.callCenterService(restTemplate, pd, paramIn.toJSONString(), apiUrl, HttpMethod.POST);

            //将unitId 刷入ImportFloor对象
            JSONObject savedUnitInfo = getExistsUnit(pd, result, importFloor);
            importFloor.setUnitId(savedUnitInfo.getString("unitId"));

        }
        return responseEntity;
    }

    private JSONObject getExistsUnit(IPageData pd, ComponentValidateResult result, ImportFloor importFloor) {
        String apiUrl = "";
        ResponseEntity<String> responseEntity = null;
        apiUrl = ServiceConstant.SERVICE_API_URL + "/api/unit.queryUnits?communityId=" + result.getCommunityId()
                + "&floorId=" + importFloor.getFloorId() + "&unitNum=" + importFloor.getFloorNum();
        responseEntity = this.callCenterService(restTemplate, pd, "", apiUrl, HttpMethod.GET);

        if (responseEntity.getStatusCode() != HttpStatus.OK) { //跳过 保存单元信息
            return null;
        }

        JSONArray savedFloorInfoResults = JSONArray.parseArray(responseEntity.getBody());

        if (savedFloorInfoResults == null || savedFloorInfoResults.size() != 1) {
            return null;
        }

        JSONObject savedUnitInfo = savedFloorInfoResults.getJSONObject(0);

        return savedUnitInfo;
    }

    private JSONObject getExistsFloor(IPageData pd, ComponentValidateResult result, ImportFloor importFloor) {
        String apiUrl = "";
        ResponseEntity<String> responseEntity = null;
        apiUrl = ServiceConstant.SERVICE_API_URL + "/api/floor.queryFloors?page=1&row=1&communityId=" + result.getCommunityId() + "&floorNum=" + importFloor.getFloorNum();
        responseEntity = this.callCenterService(restTemplate, pd, "", apiUrl, HttpMethod.GET);

        if (responseEntity.getStatusCode() != HttpStatus.OK) { //跳过 保存单元信息
            return null;
        }

        JSONObject savedFloorInfoResult = JSONObject.parseObject(responseEntity.getBody());

        if (!savedFloorInfoResult.containsKey("apiFloorDataVoList") || savedFloorInfoResult.getJSONArray("apiFloorDataVoList").size() != 1) {
            return null;
        }

        JSONObject savedFloorInfo = savedFloorInfoResult.getJSONArray("apiFloorDataVoList").getJSONObject(0);

        return savedFloorInfo;
    }

    /**
     * 查询存在的业主
     *
     * @param pd
     * @param result
     * @param importOwner
     * @return
     */
    private JSONObject getExistsOwner(IPageData pd, ComponentValidateResult result, ImportOwner importOwner) {
        String apiUrl = "";
        ResponseEntity<String> responseEntity = null;
        apiUrl = ServiceConstant.SERVICE_API_URL + "/api/owner.queryOwners?page=1&row=1&communityId=" + result.getCommunityId()
                + "&ownerTypeCd=1001&name=" + importOwner.getOwnerName() + "&link=" + importOwner.getTel();
        responseEntity = this.callCenterService(restTemplate, pd, "", apiUrl, HttpMethod.GET);

        if (responseEntity.getStatusCode() != HttpStatus.OK) { //跳过 保存单元信息
            return null;
        }

        JSONObject savedOwnerInfoResult = JSONObject.parseObject(responseEntity.getBody());

        if (!savedOwnerInfoResult.containsKey("owners") || savedOwnerInfoResult.getJSONArray("owners").size() != 1) {
            return null;
        }

        JSONObject savedOwnerInfo = savedOwnerInfoResult.getJSONArray("owners").getJSONObject(0);

        return savedOwnerInfo;
    }

    /**
     * 数据校验处理
     *
     * @param floors
     * @param owners
     * @param rooms
     * @param parkingSpaces
     */
    private void importExcelDataValidate(List<ImportFloor> floors, List<ImportOwner> owners, List<ImportRoom> rooms, List<ImportParkingSpace> parkingSpaces) {
    }

    /**
     * 获取车位信息
     *
     * @param workbook
     * @param parkingSpaces
     */
    private void getParkingSpaces(Workbook workbook, List<ImportParkingSpace> parkingSpaces, List<ImportOwner> owners) {
        Sheet sheet = null;
        sheet = ImportExcelUtils.getSheet(workbook, "车位信息");
        List<Object[]> oList = ImportExcelUtils.listFromSheet(sheet);
        ImportParkingSpace importParkingSpace = null;
        for (int osIndex = 0; osIndex < oList.size(); osIndex++) {
            Object[] os = oList.get(osIndex);
            if (osIndex == 0) { // 第一行是 头部信息 直接跳过
                continue;
            }
            importParkingSpace = new ImportParkingSpace();
            importParkingSpace.setPsNum(os[0].toString());
            importParkingSpace.setTypeCd(os[1].toString());
            importParkingSpace.setArea(Double.parseDouble(os[2].toString()));
            ImportOwner importOwner = getImportOwner(owners, os[3].toString());
            importParkingSpace.setImportOwner(importOwner);
            if (importOwner != null) {
                importParkingSpace.setCarNum(os[4].toString());
                importParkingSpace.setCarBrand(os[5].toString());
                importParkingSpace.setCarType(os[6].toString());
                importParkingSpace.setCarColor(os[7].toString());
            }

            parkingSpaces.add(importParkingSpace);
        }
    }

    /**
     * 获取 房屋信息
     *
     * @param workbook
     * @param rooms
     */
    private void getRooms(Workbook workbook, List<ImportRoom> rooms,
                          List<ImportFloor> floors, List<ImportOwner> owners) {
        Sheet sheet = null;
        sheet = ImportExcelUtils.getSheet(workbook, "房屋信息");
        List<Object[]> oList = ImportExcelUtils.listFromSheet(sheet);
        ImportRoom importRoom = null;
        for (int osIndex = 0; osIndex < oList.size(); osIndex++) {
            Object[] os = oList.get(osIndex);
            if (osIndex == 0) { // 第一行是 头部信息 直接跳过
                continue;
            }
            importRoom = new ImportRoom();
            importRoom.setRoomNum(os[0].toString());
            importRoom.setFloor(getImportFloor(floors, os[1].toString(), os[2].toString()));
            importRoom.setLayer(Integer.parseInt(os[3].toString()));
            importRoom.setSection(os[4].toString());
            importRoom.setBuiltUpArea(Double.parseDouble(os[5].toString()));
            importRoom.setImportOwner(getImportOwner(owners, os[6].toString()));
            rooms.add(importRoom);
        }
    }

    /**
     * 从导入的业主信息中获取业主，如果没有返回 null
     *
     * @param owners
     * @param ownerNum
     * @return
     */
    private ImportOwner getImportOwner(List<ImportOwner> owners, String ownerNum) {
        for (ImportOwner importOwner : owners) {
            if (ownerNum.equals(importOwner.getOwnerNum())) {
                return importOwner;
            }
        }

        return null;

    }

    /**
     * get 导入楼栋信息
     *
     * @param floors
     */
    private ImportFloor getImportFloor(List<ImportFloor> floors, String floorNum, String unitNum) {
        for (ImportFloor importFloor : floors) {
            if (floorNum.equals(importFloor.getFloorNum())
                    && unitNum.equals(importFloor.getUnitNum())) {
                return importFloor;
            }
        }

        throw new IllegalArgumentException("在楼栋单元sheet中未找到楼栋编号[" + floorNum + "],单元编号[" + unitNum + "]数据");
    }

    /**
     * 获取业主信息
     *
     * @param workbook
     * @param owners
     */
    private void getOwners(Workbook workbook, List<ImportOwner> owners) {
        Sheet sheet = null;
        sheet = ImportExcelUtils.getSheet(workbook, "业主信息");
        List<Object[]> oList = ImportExcelUtils.listFromSheet(sheet);
        ImportOwner importOwner = null;
        for (int osIndex = 0; osIndex < oList.size(); osIndex++) {
            Object[] os = oList.get(osIndex);
            if (osIndex == 0) { // 第一行是 头部信息 直接跳过
                continue;
            }
            importOwner = new ImportOwner();
            importOwner.setOwnerNum(os[0].toString());
            importOwner.setOwnerName(os[1].toString());
            importOwner.setSex("男".equals(os[2].toString()) ? "0" : "1");
            importOwner.setAge(Integer.parseInt(os[3].toString()));
            importOwner.setTel(os[4].toString());
            owners.add(importOwner);
        }
    }

    /**
     * 获取小区
     *
     * @param workbook
     * @param floors
     */
    private void getFloors(Workbook workbook, List<ImportFloor> floors) {
        Sheet sheet = null;
        sheet = ImportExcelUtils.getSheet(workbook, "楼栋单元");
        List<Object[]> oList = ImportExcelUtils.listFromSheet(sheet);
        ImportFloor importFloor = null;
        for (int osIndex = 0; osIndex < oList.size(); osIndex++) {
            Object[] os = oList.get(osIndex);
            if (osIndex == 0) { // 第一行是 头部信息 直接跳过
                continue;
            }
            importFloor = new ImportFloor();
            importFloor.setFloorNum(os[0].toString());
            importFloor.setUnitNum(os[1].toString());
            importFloor.setLayerCount(os[2].toString());
            importFloor.setLift("有".equals(os[3].toString()) ? "Y" : "N");
            floors.add(importFloor);
        }
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
}
