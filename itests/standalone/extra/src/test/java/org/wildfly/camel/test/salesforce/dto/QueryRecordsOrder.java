/*
 * Salesforce Query DTO generated by camel-salesforce-maven-plugin
 * Generated on: Fri May 04 15:50:21 CEST 2018
 */
package org.wildfly.camel.test.salesforce.dto;

import com.thoughtworks.xstream.annotations.XStreamImplicit;
import org.apache.camel.component.salesforce.api.dto.AbstractQueryRecordsBase;

import java.util.List;
import javax.annotation.Generated;

/**
 * Salesforce QueryRecords DTO for type Order
 */
@Generated("org.apache.camel.maven.CamelSalesforceMojo")
public class QueryRecordsOrder extends AbstractQueryRecordsBase {

    @XStreamImplicit
    private List<Order> records;

    public List<Order> getRecords() {
        return records;
    }

    public void setRecords(List<Order> records) {
        this.records = records;
    }
}
