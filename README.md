# study-sap-cap-cloud

https://developers.sap.com/tutorials/cloudsdk-integrate-cap.html と
https://developers.sap.com/tutorials/s4sdk-odata-service-cloud-foundry.html を参考にアプリを作ってみる

## 前提条件

1. オンプレミスのS/4 HANA環境があること
2. BusinessPartnerモジュールがODataとして公開されていること
3. Node.jsかインストール済みであること

## 補足

IDEは IntelliJ IDEA Community Edition を使用します。

## ローカルアプリからオンプレS/4へ接続するまでにやったこと
以降ほぼ公式ガイド通りです。

### cdsをインストール

```bash
npm i -g @sap/cds-dk
```

### プロジェクト作成

```bash
mvn archetype:generate -DarchetypeArtifactId=cds-services-archetype -DarchetypeGroupId=com.sap.cds -DarchetypeVersion=RELEASE \
-DartifactId=myapp -DgroupId=mygroup
```

### 各種cdsファイル を作成

以下の通りに実施

https://developers.sap.com/tutorials/cloudsdk-integrate-cap.html#3ca9066c-d309-4eab-aea3-ef2bcfda5b5e
https://developers.sap.com/tutorials/cloudsdk-integrate-cap.html#27d0f905-82a8-4e9d-b5a5-d761968d19a8

### SAP Cloud SDK を追加

rootディレクトリ直下のpomに以下を追記

```xml

			<!-- https://mvnrepository.com/artifact/com.sap.cloud.sdk/sdk-bom -->
			<dependency>
				<groupId>com.sap.cloud.sdk</groupId>
				<artifactId>sdk-bom</artifactId>
				<version>3.43.0</version>
				<type>pom</type>
			</dependency>

```

次に、srvディレクトリ配下のpomに以下を追記

```xml

<dependency>
  <groupId>com.sap.cds</groupId>
  <artifactId>cds-integration-cloud-sdk</artifactId>
</dependency>

<!-- https://mvnrepository.com/artifact/com.sap.cloud.sdk.cloudplatform/scp-cf -->
<dependency>
  <groupId>com.sap.cloud.sdk.cloudplatform</groupId>
  <artifactId>scp-cf</artifactId>
  <version>3.43.0</version>
</dependency>

<!-- https://mvnrepository.com/artifact/com.sap.cloud.sdk.s4hana/s4hana-all -->
<dependency>
  <groupId>com.sap.cloud.sdk.s4hana</groupId>
  <artifactId>s4hana-all</artifactId>
  <version>3.43.0</version>
</dependency>
```

### handlerクラスを追加

myappディレクトリ以下にhandlerディレクトリ(ディレクトリ名は任意でOK)を作成

```java
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

@Component
@ServiceName("cloud.sdk.capng")
public class BusinessPartnerReadListener implements EventHandler {

  private static final Logger logger = LoggerFactory.getLogger(BusinessPartnerReadListener.class);

  // オンプレERPへ接続させるために、ErpHttpDestinationを使用する
  private final ErpHttpDestination httpDestination = DestinationAccessor
      .getDestination("bupa")
      .asHttp().decorate(DefaultErpHttpDestination::new);

  @On(event = CdsService.EVENT_READ, entity = "cloud.sdk.capng.CapBusinessPartner")
  public void onRead(CdsReadEventContext context) throws ODataException {

    final Map<Object, Map<String, Object>> result = new HashMap<>();
    final List<BusinessPartner> businessPartners =
        new DefaultBusinessPartnerService().getAllBusinessPartner().top(10)
            .executeRequest(httpDestination);

    final List<cds.gen.cloud.sdk.capng.CapBusinessPartner> capBusinessPartners =
        convertS4BusinessPartnersToCapBusinessPartners(businessPartners, "bupa");

    capBusinessPartners.forEach(capBusinessPartner -> {
      result.put(capBusinessPartner.getId(), capBusinessPartner);
    });

    context.setResult(result.values());
  }

  @On(event = CdsService.EVENT_CREATE, entity = "cloud.sdk.capng.CapBusinessPartner")
  public void onCreate(CdsCreateEventContext context) throws ODataException {
    final BusinessPartnerService service = new DefaultBusinessPartnerService();

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

```

### ローカルからオンプレS/4 HANAへ接続してみる

接続先情報を変数に保存

```bash
export destinations='[{name: "bupa", url: "オンプレS/4のURLとポート", username: "オンプレS/4のログイン情報", password: "オンプレS/4のログイン情報"}]'
```

アプリを起動

```bash
mvn clean spring-boot:run
```

localhost:8080 にて、接続が確認できるはず

## アプリをCF環境にデプロイする

1. api endpointを設定

regionによって設定値が異なるので注意

```bash
cf api https://api.cf.eu10.hana.ondemand.com
```

2. cf環境にログイン

```bash
cf login
# emailやpasswordが必要
```

```bash
cf login -a https://api.cf.eu10.hana.ondemand.com -u メールアドレス -p "\"パスワード"\" -o サブドメイン名 -s スペース名
```

3. destination と connectivity サービスを追加する

これらを追加しないと、コード内で指定しているHttp接続記述が使えない

```bash
cf create-service destination lite my-destination
cf create-service connectivity lite my-connectivity
```

4. XSUAA サービスを追加する

これがないと、 destination サービス内に定義した認証情報を使うことが出来ないっぽい

```bash
cf create-service xsuaa application my-xsuaa
```

5. deploy用のmanifestファイル(manifest.yml)を作成する

この時点ではまだ単一アプリケーション（DBとかが無い）且つ、jarのパッケージング等手作業とするので、manifest.ymlでのデプロイとする  
もし、FrontやDB、Auth等様々なモジュールを同時にdeployする必要が発生した場合は、mta.ymlを作成したほうが簡単な様子  
see: https://answers.sap.com/questions/12689412/cloud-foundry-difference-between-mtayaml-and-manif.html

作成場所は、rootディレクトリ直下

作成例
```yaml
---
applications:

- name: firstapp
  memory: 1024M
  timeout: 300
  random-route: true
  path: srv/target/myapp-exec.jar
  buildpacks:
    - sap_java_buildpack
  env:
    TARGET_RUNTIME: Tomee
    SET_LOGGING_LEVEL: '{ROOT: INFO, com.sap.cloud.sdk: INFO}'
    JBP_CONFIG_SAPJVM_MEMORY_SIZES: 'metaspace:128m..'
    JBP_CONFIG_COMPONENTS: "jres: ['com.sap.xs.java.buildpack.jdk.SAPMachineJDK']"
  services:
    - my-xsuaa
    - my-destination
    - my-connectivity
```