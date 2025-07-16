package com.r3.developers.cordapptemplate.flowexample.workflows

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

// フローを開始するために必要な、デシリアライズされた引数を保持するクラス。
class MyFirstFlowStartArgs(
    val otherMember: MemberX500Name,
)

// メッセージを格納するクラス。CordaがVirtual Node（仮想ノード）間でデータを送受信できるようにするためには、
// @CordaSerializable アノテーションを付与する必要があります。
@CordaSerializable
class Message(
    val sender: MemberX500Name,
    val message: String,
)

// MyFirstFlowはイニシエートフロー（処理を開始する側のフロー）です。
// 対応するレスポンダーフロー（応答する側のフロー）は MyFirstFlowResponder（下記で定義）です。
// 両者を連携させるためには、同じプロトコル名を指定する必要があります。
// MyFirstFlowは ClientStartableFlow を継承します。
// これにより、Corda はこのフローがクライアントからのREST API呼び出しによって開始できることを認識します。
@InitiatingFlow(protocol = "my-first-flow")
class MyFirstFlow : ClientStartableFlow {
    // デバッグのためにフローからログメッセージを出力できると便利です。
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // Cordaには、実行時にフローにインジェクト（注入）される一連のサービスがあります。
    // フローは @CordaInject アノテーションでこれらのサービスを宣言し、利用可能にします。

    // JsonMarshallingService は JSON を操作するためのサービスを提供します。
    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    // FlowMessaging は、Virtual Node間でフローセッションを確立し、
    // ペイロードを送受信するためのサービスを提供します。
    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    // MemberLookup は、このCorDappが動作している仮想ネットワークの
    // メンバー情報を検索するためのサービスを提供します。
    @CordaInject
    lateinit var memberLookup: MemberLookup

    // フローが呼び出されると、その call() メソッドが実行されます。
    // call() メソッドには @Suspendable アノテーションを付与する必要があります。これにより、Cordaは
    // 他のフローやサービスからの応答を待つために、実行を一時停止（中断）できます。
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        // コンソールやログで何が起こっているかを追跡するための便利なロギングです。
        log.info("MFF: MyFirstFlow.call() called")

        // requestBodyをログに出力します - これはCordaでフローを開始するためのフォーマットを確認するのに役立ちます。
        log.info("MFF: requestBody: ${requestBody.getRequestBody()}")

        // JsonMarshallingService を使用して、JSON形式の requestBody を MyFirstFlowStartArgs クラスにデシリアライズします。
        val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, MyFirstFlowStartArgs::class.java)

        // 通信相手の MemberX500Name を取得します。
        val otherMember = flowArgs.otherMember

        // MemberLookup サービスから自身の情報を取得します。
        val ourIdentity = memberLookup.myInfo().name

        // 定義した Message クラスを使って、メッセージペイロードを作成します。
        val message = Message(ourIdentity, "Hello from $ourIdentity.")

        // 送信するメッセージをログに出力します。
        log.info("MFF: message.message: ${message.message}")

        // FlowMessaging サービスを使って、通信相手（otherMember）とのフローセッションを開始します。
        // これにより、相手方のVirtual Nodeで対応する MyFirstFlowResponder フローが実行されます。
        val session = flowMessaging.initiateFlow(otherMember)

        // セッションの send メソッドを使って、ペイロードを MyFirstFlowResponder フローに送信 & レスポンダーフローから応答を受信します。
        val response = session.sendAndReceive(Message::class.java, message)

        // ClientStartableFlow の戻り値は常に String 型でなければなりません。この文字列は、
        // Cordaに対してフローのステータスを問い合わせた際のRESTレスポンスとして返されます。
        return response.message
    }
}

// MyFirstFlowResponder はレスポンダーフローです。
// 対応するイニシエートフローは MyFirstFlow（上記で定義）です。
// 両者を連携させるためには、同じプロトコル名を指定する必要があります。
// レスポンダーフローは ResponderFlow を継承する必要があります。
@InitiatedBy(protocol = "my-first-flow")
class MyFirstFlowResponder : ResponderFlow {
    // デバッグのためにフローからログメッセージを出力できると便利です。
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // MemberLookup は、このCorDappが動作している仮想ネットワークの
    // メンバー情報を検索するためのサービスを提供します。
    @CordaInject
    lateinit var memberLookup: MemberLookup

    // レスポンダーフローは、イニシエートフローがセッションを介して呼び出しを行ったときに起動されます。
    // レスポンダーフローが起動されると、その call() メソッドが実行されます。
    // call() メソッドには @Suspendable アノテーションを付与する必要があります。
    // これにより、Cordaは他のフローやサービスからの応答を待つために、実行を一時停止できます。
    // call() メソッドには、Cordaによってフローセッションがパラメータとして渡されるため、
    // FlowMessagingサービスをインジェクトする必要はありません。
    @Suspendable
    override fun call(session: FlowSession) {
        // コンソールやログで何が起こっているかを追跡するための便利なロギングです。
        log.info("MFF: MyFirstResponderFlow.call() called")

        // ペイロードを受信し、Message クラスにデシリアライズします。
        val receivedMessage = session.receive(Message::class.java)

        // 受信したメッセージに対して何らかの有用な操作を行う代わりに、ここではログに出力します。
        log.info("MFF: Message received from ${receivedMessage.sender}: ${receivedMessage.message} ")

        // MemberLookup サービスから自身の情報を取得します。
        val ourIdentity = memberLookup.myInfo().name

        // 送信者への挨拶として、応答メッセージを作成します。
        val response =
            Message(
                ourIdentity,
                "Hello ${session.counterparty.commonName}, best wishes from ${ourIdentity.commonName}",
            )

        // 送信する応答をログに出力します。
        log.info("MFF: response.message: ${response.message}")

        // フローセッションの send メソッドを使って、応答を送信します。
        session.send(response)
    }
}
/*
REST経由でこのフローをトリガーするためのRequestBodyサンプル:
{
    "clientRequestId": "r1",
    "flowClassName": "com.r3.developers.cordapptemplate.flowexample.workflows.MyFirstFlow",
    "requestBody": {
        "otherMember":"CN=Bob, OU=Test Dept, O=R3, L=London, C=GB"
        }
}
*/
