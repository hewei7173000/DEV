import org.json.JSONObject
import org.json.JSONArray

// 获取 headers
def headers = exchange.getMessage().getHeaders()

// 解析 returnData 字符串为 JSON 对象
def jsonStr = headers.get("returnData")
def fkObj = new JSONObject(jsonStr)

// 获取 vouchers 数组
def vouchers = fkObj.get("requestData").getJSONArray("vouchers")

//定义一个化试的json对象
def  hsData  = new JSONObject()
def  hsBodyArray = new JSONArray()


// 遍历费控的json对象，做字段映射
for (int i = 0; i < vouchers.length(); i++) {
    def voucher = vouchers.getJSONObject(i)
    // 凭证头
    if(i==0){
      hsData.put("caccid",voucher.get("acctgEntCode"))
      hsData.put("enter",voucher.get("prepByCode"))
      hsData.put("date",voucher.get("vchGenDate"))
      hsData.put("fiscal_year",voucher.get("acctgPerYr"))
      hsData.put("accounting_period",voucher.get("acctgPerMth"))
      
      hsData.put("voucher_id","")
      hsData.put("reserve2",voucher.get("preVchHdrCode"))
      
      //为返回给费控的结果里提前添加一个字段vchSrcId塞到headers
      headers.put("vchSrcId",voucher.get("vchSrcId"))
  
    }
    
    // 遍历vchLines对象
    def vchLines = voucher.get("vchLines")
    for(int j=0;j<vchLines.length();j++){
        def vchLine = vchLines.getJSONObject(j)
        def obj  = new JSONObject()
        
        obj.put("abstract",vchLine.get("vchLineDescr"))
        obj.put("account_code",vchLine.get("acctCode"))
        obj.put("currency",vchLine.get("origCurrCode"))//待确定
        
        //构造auxiliaryAccounting对象
        def auxiliaryAccounting = new JSONObject()
        auxiliaryAccounting.put("ccus_id","")
        auxiliaryAccounting.put("supplier_id","")
        auxiliaryAccounting.put("cdept_id","")
        auxiliaryAccounting.put("personnel_id","")//待确定
        auxiliaryAccounting.put("item_class","00")
        auxiliaryAccounting.put("item_id","")
        auxiliaryAccounting.put("operator","")//待确定  无需传值
        auxiliaryAccounting.put("self_define1","")
        
        
        
        obj.put("document_id","")
        obj.put("document_date","")
        obj.put("exchange_rate2",vchLine.get("currExchRate"))
        obj.put("debit_quantity",0)
        obj.put("credit_quantity",0)
        def acctCode = vchLine.get("acctCode")
        if(vchLine.has("drcrDirFlag") && !vchLine.isEmpty()){
            if(vchLine.get("drcrDirFlag") == "DR"){
                obj.put("natural_debit_currency", vchLine.get("stdCurrAmt"))
                obj.put("primary_debit_amount", vchLine.get("origCurrAmt"))
                
                if (acctCode.startsWith("1001") || acctCode.startsWith("1002")) {
                    hsData.put("voucher_type","收") 
                }
            }else{
                obj.put("natural_debit_currency", 0)
                obj.put("primary_debit_amount", 0)
                hsData.put("voucher_type","转") 
            }
            if(vchLine.get("drcrDirFlag") == "CR"){
                obj.put("natural_credit_currency", vchLine.get("stdCurrAmt"))
                obj.put("primary_credit_amount", vchLine.get("origCurrAmt"))
                
                if (acctCode.startsWith("1001") || acctCode.startsWith("1002")) {
                    hsData.put("voucher_type","付") 
                }
            }else{
                obj.put("natural_credit_currency", 0)
                obj.put("primary_credit_amount", 0)
                hsData.put("voucher_type","转") 
            }
            
        }
        obj.put("settlement","")
        
        //构造cash_flow数组
        def  analyticAcctgItems = vchLine.get("analyticAcctgItems")
        def  cashFlows  = new JSONArray()//业务上一行分录就只有一个现金流 也就是cashFlows里只有一个cashFlow
        def  cashFlow = new JSONObject()//业务上一行分录就只有一个现金流 所以只定义一个实体
        if(!(analyticAcctgItems == JSONObject.NULL || analyticAcctgItems == null)){
            for(Object analyticAcctgItem: analyticAcctgItems){
                def itemTypeCode = analyticAcctgItem.get("analyticAcctgItemTypeCode")
                def itemCode = analyticAcctgItem.get("analyticAcctgItemCode")
                if(itemTypeCode == "CASH_FLOW_CODE"){
                    cashFlow.put("cCashItem", itemCode)
                    cashFlow.put("ccode", vchLine.get("acctCode"))
                    if(vchLine.get("drcrDirFlag") == "DR"){
                        cashFlow.put("md", vchLine.get("stdCurrAmt"))
                        cashFlow.put("md_f", vchLine.get("origCurrAmt"))
                    }
                    if(vchLine.get("drcrDirFlag") == "CR"){
                        cashFlow.put("mc", vchLine.get("stdCurrAmt"))
                        cashFlow.put("mc_f", vchLine.get("origCurrAmt"))
                    }
                    cashFlow.put("nd_s", 0)
                    cashFlow.put("nc_s", 0)
                    cashFlows.put(cashFlow)
                }
                if(itemTypeCode == "CUST_CODE"){
                    auxiliaryAccounting.put("ccus_id",itemCode)//待确定
                }
                if(itemTypeCode == "VNDR_CODE"){
                    auxiliaryAccounting.put("supplier_id",itemCode)//待确定
                }
                if(itemTypeCode == "FZDEPT"){
                    auxiliaryAccounting.put("cdept_id",itemCode)//待确定
                }
                if(itemTypeCode == "EMP_CODE"){
                    auxiliaryAccounting.put("personnel_id",itemCode)//待确定
                }
                if(itemTypeCode == "PRJ_CODE"){
                    auxiliaryAccounting.put("item_id",itemCode)
                }
                if(itemTypeCode == "INTER_CODE"){
                    auxiliaryAccounting.put("self_define1",itemCode)
                }              
            }
        }
        
        obj.put("cash_flow",cashFlows)
        obj.put("auxiliary_accounting",auxiliaryAccounting)
        hsBodyArray.put(obj)
    } 
}
hsData.put("body",hsBodyArray)
// 更新 headers.returnData为化试需要的格式
exchange.getMessage().setBody(hsData.toString())
exchange.getMessage().setHeader("Authorization","Bearer "+headers.get("targetAppkey"))
exchange.getMessage().setHeader("Content-Type", "application/json;charset=utf-8")