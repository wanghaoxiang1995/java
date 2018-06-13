
## API交互
* Elasticsearch REST API 在HTTP上使用JSON进行交互
* 除非特别指定，可以通过REST API使用以下的API：
* * Multiple Indices
* * Date math support in index names
* * Common options
* * URL-based access control

### Multiple Indices
* 多数APIs通过执行multiple indices支持```index```参数，使用简单的```test1,test2,test3```标记（或者```_all```代表全部索引）。也支持通配符，例如：```test*```、```*test```、```te*t```或```*test*```，并且可以进行排除（```-```），如：```test*,-test3```。
