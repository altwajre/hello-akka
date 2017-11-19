curl -X GET http://localhost:8088/randomOrder


curl -X POST -H "Content-Type: application/json" --data '{"deliveryPrice":95.3433758801223,"timestamp":1488135061123, "items": [{"id":0,"quantity":42,"unitPrice":65.01159569545462, "percentageDiscount":0.14585908649640444}, {"id":1,"quantity":7,"unitPrice":27.047124705161696, "percentageDiscount":0.06400701658372476}, {"id":2,"quantity":76,"unitPrice":24.028733083343724, "percentageDiscount":0.9906003213266685}, {"id":3,"quantity":18,"unitPrice":88.77181117560474, "percentageDiscount":0.8203117015522584}, {"id":4,"quantity":15,"unitPrice":29.73662623732769}], "id":"randomId","metadata":{"notes":"random"}}' http://localhost:8088/calculateGrandTotal
