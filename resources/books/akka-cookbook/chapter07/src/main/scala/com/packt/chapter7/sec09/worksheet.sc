val numberOfShards = 100

var location = "USA-Chicago"
location.hashCode
(s"$location".hashCode % numberOfShards).toString

location = "ZA-CapeTown"
location.hashCode
(s"$location".hashCode % numberOfShards).toString

