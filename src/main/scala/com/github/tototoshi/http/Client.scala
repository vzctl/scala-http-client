package com.github.tototoshi.http

import java.io.File
import java.net.ProxySelector
import java.util.{ ArrayList, List => JList }
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.entity.StringEntity
import org.apache.http.client.methods.{ HttpGet, HttpPost, HttpPut }
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.impl.conn.ProxySelectorRoutePlanner
import org.apache.http.message.BasicNameValuePair
import org.apache.http.{ HttpResponse, NameValuePair }
import org.apache.http.util.EntityUtils
import net.liftweb.json.JsonParser._
import net.liftweb.json.JValue
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.conn.SchemeRegistryFactory
import org.apache.http.params.SyncBasicHttpParams
import org.apache.http.client.HttpClient

trait Using {

  type Closable = { def close(): Unit }

  def using[A <: Closable, B](resource: A)(f: A => B) = {
    try {
      f(resource)
    } finally {
      resource.close
    }
  }

}

object Client {

  def defaultClient: HttpClient = {
    val params = new SyncBasicHttpParams()
    DefaultHttpClient.setDefaultHttpParams(params)
    new DefaultHttpClient(new ThreadSafeClientConnManager(SchemeRegistryFactory.createDefault()), params)
  }

}

class Client(httpClient: HttpClient) extends Using {
  def this() = this(Client.defaultClient)

  private def constructNameValuePairs(data: Iterable[(String, String)]): JList[NameValuePair] = {
    data.foldLeft(new ArrayList[NameValuePair](data.size)) {
      case (pairs, (k, v)) => { pairs.add(new BasicNameValuePair(k, v)); pairs }
    }
  }

  def GET(url: String, param: Iterable[(String, String)] = Map(), header: Map[String, String] = Map(), encoding: String = "UTF-8"): Response = {
    val urlWithParams = if (param.isEmpty) url else url + "?" + URLEncodedUtils.format(constructNameValuePairs(param), encoding)
    val request = new HttpGet(urlWithParams)
    header foreach { case (k, v) => request.addHeader(k, v) }
    new Response(httpClient.execute(request))
  }

  def POST(url: String, body: String): Response = {
    POST(url, body, Map[String, String](), "UTF-8")
  }

  def POST(url: String, body: String, header: Map[String, String], encoding: String): Response = {
    val request = new HttpPost(url)
    header foreach { case (k, v) => request.addHeader(k, v) }
    request.setEntity(new StringEntity(body, encoding))
    new Response(httpClient.execute(request))
  }

  def POST(url: String, body: Array[Byte], header: Map[String, String]): Response = {
    val request = new HttpPost(url)
    header foreach { case (k, v) => request.addHeader(k, v) }
    request.setEntity(new ByteArrayEntity(body))
    new Response(httpClient.execute(request))
  }

  def PUT(url: String, body: String = "", header: Map[String, String] = Map(), encoding: String = "UTF-8"): Response = {
    val request = new HttpPut(url)
    header foreach { case (k, v) => request.addHeader(k, v) }
    request.setEntity(new StringEntity(body, encoding))
    new Response(httpClient.execute(request))
  }
}

class Response(httpResponse: HttpResponse) extends Using {

  def getHttpResponse = httpResponse

  def consume = EntityUtils.consume(httpResponse.getEntity)

  def statusCode(consume: Boolean = true): Int = {
    if (consume) {
      EntityUtils.consume(httpResponse.getEntity)
    }
    httpResponse.getStatusLine.getStatusCode
  }

  def asString(): String = asString("UTF-8")

  def asString(charset: String): String = {
    if (httpResponse.getEntity == null) "" else {
      val res = EntityUtils.toString(httpResponse.getEntity, charset)
      EntityUtils.consume(httpResponse.getEntity)
      res
    }
  }

  def asJson(): JValue = parse(asString)

  def asBytes(): Array[Byte] = {
    if (httpResponse.getEntity == null) Array() else {
      val res = EntityUtils.toByteArray(httpResponse.getEntity())
      EntityUtils.consume(httpResponse.getEntity)
      res
    }
  }

  def save(filename: String): Unit = save(new File(filename))

  def save(file: File): Unit = {
    if (httpResponse.getEntity != null) {
      using(new java.io.BufferedInputStream(httpResponse.getEntity.getContent)) { in =>
        using(new java.io.PrintStream(file)) { out =>
          val buffer = new Array[Byte](8192)
          Stream.continually(in.read(buffer)).takeWhile(_ >= 0) foreach {
            out.write(buffer, 0, _)
          }
        }
      }
    }
  }

}
