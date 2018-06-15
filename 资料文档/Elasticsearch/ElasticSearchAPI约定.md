
## API交互
* Elasticsearch REST API 在HTTP上使用JSON进行交互
* 除非特别指定，可以通过REST API使用以下的API：
  * Multiple Indices
  * Date math support in index names
  * Common options
  * URL-based access control

### Multiple Indices
* 多数APIs通过执行multiple indices支持```index```参数，使用简单的```test1,test2,test3```标记（或者```_all```代表全部索引）。也支持通配符，例如：```test*```、```*test```、```te*t```或```*test*```，并且可以进行排除（```-```），如：```test*,-test3```。
* 所有的multi indices API支持下面的url query string参数
  * ```ignore_unavailable``` 控制当有任何指定的索引不可用（包括不存在的或关闭的索引）时，是否忽略。可以指定为```true|false```
  * ```allow_no_indices``` 控制一个索引通配表达式没有实际的索引时，是否失败。可以指定为```true|false```。例如使用通配表达式foo*并且没有以foo开始的可用索引会根据这个设定决定请求是否为fail。这个设定同样适用于_all，*或没有指定索引。在一个别名指向一个关闭的索引时，这个设定同样可以应用到别名
  * ```expand_wildcards``` 控制索引通配表达式拓展的具体类型。如果指定为```open```则通配表达式仅在打开的索引上拓展，如果指定为```closed```则通配表达式仅在关闭的索引上拓展，同时指定（```open,closed```）指定拓展到全部索引。如果指定为```none```，关闭通配，如果指定为```all```通配表达式拓展到全部索引（与指定为```open,closed```相同）
  * 以上参数的默认值取决于使用的api
* 单索引APIs如Document APIs和single-index alias APIs不支持 multiple indices

### Date math support in index names
* 日期计算索引名称解析允许你搜索一个时间序列范围内的索引，而不是搜索所有时间序列索引并在结果中过滤，或者维护别名。限定索引数量减少集群搜索负载，并且提高搜索性能。例如，如果搜索你每日的错误日志，可以使用一个 date math name template 来限定搜索到两天内。
* 几乎所有具有 index 参数的索引，在```index```参数中支持时间序列。一个时间序列索引名通过以下形式获得
```<static_name{date_math_expr{date_format|time_zone}}>```
  * ```static_name``` 为名称的静态文本部分
  * ```date_math_expr``` 为动态计算时间的动态时间表达式
  * ```date_format``` 为日期计算应该使用的格式选项，默认为```YYYY.MM.dd```
  * ```time_zone``` 时区选项，默认为```utc```
* 你必须使用方括号包围你的date math index name expressions，并且所有特殊字符应该被URI encoded，例如
  ```$xslt
  # GET /<logstash-{now/d}>/_search
  GET /%3Clogstash-%7Bnow%2Fd%7D%3E/_search
  {
    "query" : {
      "match": {
        "test": "data"
      }
    }
  }
  ```
* 日期数值字符URI编码
  |||
  |-|-|
  |<|%3C|
  |>|%3E|
  |/|%2F|
  |{|%7B|
  |}|%7D|
  |`|`|%7C|
  |+|%2B|
  |:|%3A|
  |,|%2C|

* 下面的形式显示解析 22rd March 2024 noon utc 为当前时间，解析到不同日期数值索引名格式和最终解析的索引名

|Expression|Resolves to|
|-|-|
|`<logstash-{now/d}>`|`logstash-2024.03.22`|
|`<logstash-{now/M}>`|`logstash-2024.03.01`|
|`<logstash-{now/M{YYYY.MM}}>`|`logstash-2024.03`|
|`<logstash-{now/M-1M{YYYY.MM}}>`|`logstash-2024.02`|
|`<logstash-{now/d{YYYY.MM.dd|+12:00}}>`|`logstash-2024.03.23`|

* 为了在索引名称模板的静态部分使用字符`{`和`}`，使用反斜杠`\`来escape，如`<elastic\\{ON\\}-{now/M}>` 解析到 `elastic{ON}-2024.03.01`
* 下面是一个搜索过去三天Logstash索引的搜索请求例子，嘉定索引使用默认Logstash索引名称格式`logstash-YYYY.MM.dd`
  ```$xslt
  # GET /<logstash-{now/d-2d}>,<logstash-{now/d-1d}>,<logstash-{now/d}>/_search
  GET /%3Clogstash-%7Bnow%2Fd-2d%7D%3E%2C%3Clogstash-%7Bnow%2Fd-1d%7D%3E%2C%3Clogstash-%7Bnow%2Fd%7D%3E/_search
  {
    "query" : {
      "match": {
        "test": "data"
      }
    }
  }
  ```

### Common options
* 下列选项可以应用到REST APIs
  * `Pretty Results` 当进行任何请求时附加一个`?pretty=true`，返回的JSON将会格式化梅花（只在调试时使用）。另一个选项是设置`?format=yaml`将会使返回值使用一个（有时）可读性更好的yaml格式
  * `Human readable output` 将返回更适合人类的统计值（如`"exists_time": "1h"`或`"size": "1kb"`）和社和计算机的（如`"exists_time_in_millis": 3600000`或`"size_in_bytes":1024`）。人类可读的值可以在query string中添加`?human=false`来关闭。当统计结果被一个监控工具消费而不是被人类消费时，这是有意义的。默认的`human`标记为`false`
  * `Date Math` 多数参数接受一个格式化的时间值，例如在`range queries`中的`gt`和`lt`，或`daterange aggregations`中的`from`和`to`会理解日期计算。表达式以一个指定时间开始，包括`now`或者一个以`||`结尾的日期字符串。固定时间可以使用以下一个或多个计算表达式：
    * `+1h` - 加一小时
    * `-1d` - 减一天
    * `/d` - 取整到最近一天
    * 支持的时间单位和持续时间的时间单位不同，支持的时间单位为：
|||
|-|-|
|`y`|年|
|`M`|月|
|`w`|周|
|`d`|天|
|`h`|时|
|`H`|时|
|`m`|分|
|`s`|秒|

    * 假设`now`为`2001-01-01 12:00:00`，一些例子为：
    ```
    now+1h
        now in milliseconds plus one hour. Resolves to: 2001-01-01 13:00:00 
    now-1h
        now in milliseconds minus one hour. Resolves to: 2001-01-01 11:00:00 
    now-1h/d
        now in milliseconds minus one hour, rounded down to UTC 00:00. Resolves to: 2001-01-01 00:00:00 
    2001.02.01\|\|+1M/d
        2001-02-01 in milliseconds plus one month. Resolves to: 2001-03-01 00:00:00
    ``` 
      * `Response Filtering` 所有REST APIs接受一个`filter_path`参数用来减少Elasticsearch返回的响应。这个参数使用逗号分割使用`.`标记的过滤表达式列表：
    `GET /_search?q=elasticsearch&filter_path=took,hits.hits._id,hits.hits._score`
    ```$xslt
    Responds:
    {
      "took" : 3,
      "hits" : {
        "hits" : [
          {
            "_id" : "0",
            "_score" : 1.6375021
          }
        ]
      }
    }
    ```
    * 同样支持使用*通配符匹配全部字段或字段名的部分
    `GET /_cluster/state?filter_path=metadata.indices.*.stat*`
    ```$xslt
    {
      "metadata" : {
        "indices" : {
          "twitter": {"state": "open"}
        }
      }
    }
    ```
    * `**`通配符用来包含不知道字段确切路径时的字段名。例如，我们可以使用以下请求返回Lucene版本的每个片段：
    `GET /_cluster/state?filter_path=routing_table.indices.**.state`
    ```$xslt
    Responds:
    {
      "routing_table": {
        "indices": {
          "twitter": {
            "shards": {
              "0": [{"state": "STARTED"}, {"state": "UNASSIGNED"}],
              "1": [{"state": "STARTED"}, {"state": "UNASSIGNED"}],
              "2": [{"state": "STARTED"}, {"state": "UNASSIGNED"}],
              "3": [{"state": "STARTED"}, {"state": "UNASSIGNED"}],
              "4": [{"state": "STARTED"}, {"state": "UNASSIGNED"}]
            }
          }
        }
      }
    }
    ```
    * 同样可以使用前缀`-`排除一个或多个字段：`GET /_count?filter_path=-_shards`
    ```$xslt
    Responds:
    {
      "count" : 5
    }
    ```
    * 为了更多的控制，可以在一个表达式中同时使用包含和排除过滤。这种情况下，会先应用排除过滤，然后再进行包含过滤
    `GET /_cluster/state?filter_path=metadata.indices.*.state,-metadata.indices.logstash-*`
    ```$xslt
    Responds：
    {
      "metadata" : {
        "indices" : {
          "index-1" : {"state" : "open"},
          "index-2" : {"state" : "open"},
          "index-3" : {"state" : "open"}
        }
      }
    }
    ```
    * 注意Elasticsearch有时直接返回字段未加工的值，就像`_source`字段。如果你想要过滤`_source`字段，你应该考虑结合已经存在的`_source`参数与`filter_path`参数：
    ```$xslt
    POST /library/book?refresh
    {"title": "Book #1", "rating": 200.1}
    POST /library/book?refresh
    {"title": "Book #2", "rating": 1.7}
    POST /library/book?refresh
    {"title": "Book #3", "rating": 0.1}
    GET /_search?filter_path=hits.hits._source&_source=title&sort=rating:desc
    ```
    ```$xslt
    {
      "hits" : {
        "hits" : [ {
          "_source":{"title":"Book #1"}
        }, {
          "_source":{"title":"Book #2"}
        }, {
          "_source":{"title":"Book #3"}
        } ]
      }
    }
    ```
  * `Flat Settings` `flat_settings`标记影响设定列表的翻译。当`flat_settings`标记为`true`，设定会返回扁平化格式：`GET twitter/_settings?flat_settings=true`
    ```$xslt
    Returns:
    {
      "twitter" : {
        "settings": {
          "index.number_of_replicas": "1",
          "index.number_of_shards": "1",
          "index.creation_date": "1474389951325",
          "index.uuid": "n6gzFZTgS664GUfx0Xrpjw",
          "index.version.created": ...,
          "index.provided_name" : "twitter"
        }
      }
    }
    ```
    * 当`flat_settings`标记为`false`时，settings会返回更人类可读的结构化格式：`GET twitter/_settings?flat_settings=false`
    ```$xslt
    Returns:
    {
      "twitter" : {
        "settings" : {
          "index" : {
            "number_of_replicas": "1",
            "number_of_shards": "1",
            "creation_date": "1474389951325",
            "uuid": "n6gzFZTgS664GUfx0Xrpjw",
            "version": {
              "created": ...
            },
            "provided_name" : "twitter"
          }
        }
      }
    }
    ```
    * `flat_settings`默认设定为`false`
  * `Parameters` REST参数（当使用HTTP，map to HTTP URL parameters）遵从使用下划线风格的约定
  * `Boolean Values` 所有的REST APIs参数（包括请求参数和JSON body）支持使用"true"和"false"指定布尔值`true|false`。所有其他的值会引起一个错误。
  * `Number Values` 所有的REST APIs支持数值参数为字符串，支持JSON天然数值类型。
  * `Time units` 在任何需要指定持续时间时，例如`timeout`参数，持续时间必须指定单位，如`2d`为两天，支持的单位如下：
|||
|-|-|
|`d`|天|
|`h`|小时|
|`m`|分|
|`s`|秒|
|`ms`|毫秒|
|`micros`|微秒|
|`nanos`|纳秒|
  * `Byte size units` 当需要指定数据字节大小时，如设定一个buffer size 参数时，必须指定值的单位，如`10kb`。注意单位使用1024进位，支持的单位为：
|||
|-|-|
|`b`|Bytes|
|`kb`|Kilobytes|
|`mb`|Megabytes|
|`gb`|Gigabytes|
|`tb`|Terabytes|
|`pb`|Petabytes|
  * `Unit-less quantities` 无数量单位意味着使用单位"bytes"或"Hertz"或"meter"或"long tonne"，如果数量过大，ELasticsearch将会输出像10m为10,000,000或者7k为7,000。这意味着当87时，仍会输出87。这些是支持的乘数：
|||
|-|-|
|‘’|Single|
|`k`|Kilo|
|`m`|Mega|
|`g`|Giga|
|`t`|Tera|
|`p`|Peta|
  * `Distance Units` 但需要指定距离时（如`Geo Distance Query`中的`distance`参数）如果没有指定时默认单位为米。距离可以指定为其他单位，如"1km"或"2mi"（2miles）。单位列表为：
|||
|-|-|
|Mile|`mi|miles`|
|Yard|`yd|yards`|
|Feet|`ft|feet`|
|Inch|`in|inch`|
|Kilometer|`km|kilometers`|
|Meter|`m|meters`|
|Centimeter|`cm|centimeters`|
|Millimeter|`mm|millimeters`|
|Nautical mile|`NM,nmi|natuicalmiles`|
  * `Fuzziness` 一些queries和APIs支持运行模糊匹配的参数，使用`fuzziness`参数，当querying `text`或`keyword`字段，`fuzziness`会解释为`Levenshtein Edit Distance`——使一个字符串与另一个字符串相同需要改变的字符数。`fuzziness`参数可以像这样指定：`0`，`1`，`2`最大允许的修改数量，`AUTO`局域term长度生成一个edit distance。可以选择`AUTO:[low],[high]`指定low and high distance arguments，如果没有指定，默认值为3和6，即`AUTO:3,6`，在以下长度将会：
    * `0..2` 必须完全匹配
    * `3..5` 允许单个edit
    * `>5` 允许两个edits
    * 通常偏向与用`AUTO`作为`fuzziness`的值
  * `Enabling stack trances` 当请求返回一个错误时，Elasticsearch默认不会包含错误的stack trace，你可以通过设置`error_trace` url参数为`true`来开启这个行为。例如，当发送一个非法的size参数到_search API：
    `POST /twitter/_search?size=surprise_me`
    ```
    响应会像这样
    {
      "error" : {
        "root_cause" : [
          {
            "type" : "illegal_argument_exception",
            "reason" : "Failed to parse int parameter [size] with value [surprise_me]"
          }
        ],
        "type" : "illegal_argument_exception",
        "reason" : "Failed to parse int parameter [size] with value [surprise_me]",
        "caused_by" : {
          "type" : "number_format_exception",
          "reason" : "For input string: \"surprise_me\""
        }
      },
      "status" : 400
    }
    ```
    * 但是当设置`error_trace=true`时
    `POST /twitter/_search?size=surprise_me&error_trace=true`
    ```
    响应为
    {
      "error": {
        "root_cause": [
          {
            "type": "illegal_argument_exception",
            "reason": "Failed to parse int parameter [size] with value [surprise_me]",
            "stack_trace": "Failed to parse int parameter [size] with value [surprise_me]]; nested: IllegalArgumentException..."
          }
        ],
        "type": "illegal_argument_exception",
        "reason": "Failed to parse int parameter [size] with value [surprise_me]",
        "stack_trace": "java.lang.IllegalArgumentException: Failed to parse int parameter [size] with value [surprise_me]\n    at org.elasticsearch.rest.RestRequest.paramAsInt(RestRequest.java:175)...",
        "caused_by": {
          "type": "number_format_exception",
          "reason": "For input string: \"surprise_me\"",
          "stack_trace": "java.lang.NumberFormatException: For input string: \"surprise_me\"\n    at java.lang.NumberFormatException.forInputString(NumberFormatException.java:65)..."
        }
      },
      "status": 400
    }
    ```
  * `Request body in query string` 对于不接受非POST请求的库，可以通过设置请求体为`source` query string 参数代替。当使用这种方式，`source_content_type`参数也应该传递一个媒体类型的值，来制定source的格式，如application/json
  * `Content-Type Requirements` 请求体发送的内容类型必须使用`Content-Type`头指定，请求头的值必须映射到该API支持的一种格式。多数API支持JSON，YAML，CBOR和SMILE。bulk和multi-searchAPIs支持NDJSON（Newline delimited JSON），JSON和SMILE，而其他类型将会导致错误响应。另外，当使用`source` query string参数时内容类型必须使用`source_content_type` query string参数指定。

### `URL-based access conrtol`
* 许多用户使用一个基于URL的访问控制代理来安全的访问Elasticsearch索引，对于multi-search，multi-get和bulk请求，用户可以选择在URL中指定一个索引，也可以在每个请求体中的单独请求中指定。这让基于URL的访问控制受到挑战。防止用户覆盖URL中指定的索引，在`elasticsearch.yml`文件中添加设置`rest.action.multi.allow_explicit_index: false`，默认值为`true`当指定为`false`时，Elasticsearch将拒绝在请求体中指定一个确定索引的请求
