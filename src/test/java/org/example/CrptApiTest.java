package org.example;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static org.example.CrptApi.DocumentsType.LP_INTRODUCE_GOODS;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrptApiTest {
    CrptApi.Document document;
    CrptApi.DocumentDTO documentDTO;
    CrptApi.Product product1;
    CrptApi.Product product2;
    List<CrptApi.Product> products = new ArrayList<>();

    @BeforeEach
    public void setUp() {

//        private final CrptApi.DocService docService = crptApi.new DocService();


        product1 = new CrptApi.Product(
                "СГР", // документ о сертификации (сертификат) продукта
                "2015-09-15", // дата сертификации (?) продукта
                "RU.77.99.88.003.Е.00904", // номер сертификата на продукт
                "7813562961", // ИНН владельца продукта
                "7804063927", //  ИНН производителя продукта
                "2024-01-15", // дата производства продукта
                "2783801001", // код ТН ВЭД
                "03920155", // код УКТ ВЭД
                "3562"  // код УТ ВЭДУ
        );
        products.add(product1);
        product2 = new CrptApi.Product("СГР",
                "2015-08-26",
                "RU.77.99.11.003.Е.008651.08.15",
                "7813562961",
                "7804063927",
                "2024-02-15",
                "2783801001",
                "03920155",
                "3562");
        products.add(product2);

        document = new CrptApi.Document("ok",
                LP_INTRODUCE_GOODS,
                true,
                "7813562961",
                "7813562961",
                "7804063927",
                "2024-01-21",
                "БАД",
                products,
                "2023-11-19",
                "123456789");
        documentDTO = CrptApi.mapper.fromDocumentToDto(document);
    }
// ********** закончили с подготовкой информации для документа *****************

    @Test
    @DisplayName("Проверяем преобразование Product в ProductDTO")
    void testFromProductToDTO() {
        CrptApi.ProductDTO productDTO = CrptApi.mapper.fromProductToDTO(product1);
        assertEquals(productDTO.getOwner_inn(), product1.getOwner_inn());
    }

    @Test
    @DisplayName("Проверяем преобразование списка Product в ProductDTO")
    public void createProductDTOListTest() {
        List<CrptApi.ProductDTO> productDTOList = CrptApi.mapper.createProductDTOList(products);
        assertEquals(2, products.size());
        assertEquals(2, productDTOList.size());
    }

    @Test
    @DisplayName("Проверяем корректность созданного документа")
    public void createDocumentTest() {
        assertEquals("7804063927", document.getProducer_inn());
        assertEquals("2024-01-21", document.getProduction_date());
    }
}