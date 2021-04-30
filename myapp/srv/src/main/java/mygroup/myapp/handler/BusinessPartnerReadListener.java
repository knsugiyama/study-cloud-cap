package mygroup.myapp.handler;

import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.odatav2.connectivity.ODataException;
import com.sap.cloud.sdk.s4hana.connectivity.DefaultErpHttpDestination;
import com.sap.cloud.sdk.s4hana.connectivity.ErpHttpDestination;
import com.sap.cloud.sdk.s4hana.datamodel.odata.namespaces.businesspartner.BusinessPartner;
import com.sap.cloud.sdk.s4hana.datamodel.odata.services.BusinessPartnerService;
import com.sap.cloud.sdk.s4hana.datamodel.odata.services.DefaultBusinessPartnerService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * イベントハンドラークラスは、SpringのComponentとして登録する必要がある ServiceNameに定義した値が、このクラスのメソッドに適用されるデフォルトのサービス名となる
 */
@Component
@ServiceName("cloud.sdk.capng")
public class BusinessPartnerReadListener implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(BusinessPartnerReadListener.class);

  // BTP上に定義するDestinationを定義する?
//  private final HttpDestination httpDestination = DestinationAccessor.getDestination("MyErpSystem")
//      .asHttp();

  // ERPへ繋がる設定も入れられる様子
  private final ErpHttpDestination httpDestination = DestinationAccessor
      .getDestination("bupa")
      .asHttp().decorate(DefaultErpHttpDestination::new);

  /**
   * S/4のBusinessPartnerの最初の10件を返却する。Onアノテーションを使用することでイベント制御することができる
   *
   * @param context
   * @throws ODataException
   */
  @On(event = CdsService.EVENT_READ, entity = "cloud.sdk.capng.CapBusinessPartner")
  public void onRead(CdsReadEventContext context) throws ODataException {

    final Map<Object, Map<String, Object>> result = new HashMap<>();
    final List<BusinessPartner> businessPartners =
        new DefaultBusinessPartnerService().getAllBusinessPartner().top(50)
            .executeRequest(httpDestination);

    final List<cds.gen.cloud.sdk.capng.CapBusinessPartner> capBusinessPartners =
        convertS4BusinessPartnersToCapBusinessPartners(businessPartners, "bupa");

    capBusinessPartners.forEach(capBusinessPartner -> {
      result.put(capBusinessPartner.getId(), capBusinessPartner);
    });

    context.setResult(result.values());
  }

  /**
   * S/4にデータを登録する
   *
   * @param context
   * @throws ODataException
   */
  @On(event = CdsService.EVENT_CREATE, entity = "cloud.sdk.capng.CapBusinessPartner")
  public void onCreate(CdsCreateEventContext context) throws ODataException {
    final BusinessPartnerService service = new DefaultBusinessPartnerService();

    // CQSを使うと、モデルで定義されたサービス以外にもデータベースなどとも通信できる
    Map<String, Object> m = context.getCqn().entries().get(0);
    BusinessPartner bp = BusinessPartner.builder().firstName(m.get("firstName").toString())
        .lastName(m.get("surname").toString()).businessPartner(m.get("ID").toString()).build();

    service.createBusinessPartner(bp).executeRequest(httpDestination);
  }

  private List<cds.gen.cloud.sdk.capng.CapBusinessPartner> convertS4BusinessPartnersToCapBusinessPartners(
      List<BusinessPartner> businessPartners, String destination) {

    final List<cds.gen.cloud.sdk.capng.CapBusinessPartner> capBusinessPartners = new ArrayList<>();

    for (final BusinessPartner s4BusinessPartner : businessPartners) {
      final cds.gen.cloud.sdk.capng.CapBusinessPartner capBusinessPartner = com.sap.cds.Struct
          .create(
              cds.gen.cloud.sdk.capng.CapBusinessPartner.class);

      capBusinessPartner.setFirstName(s4BusinessPartner.getFirstName());
      capBusinessPartner.setSurname(s4BusinessPartner.getLastName());
      capBusinessPartner.setId(s4BusinessPartner.getBusinessPartner());
      capBusinessPartner.setSourceDestination(destination);

      capBusinessPartners.add(capBusinessPartner);

    }
    return capBusinessPartners;
  }
}
