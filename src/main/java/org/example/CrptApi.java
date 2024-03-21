package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    TimeUnit timeUnit;
    int requestLimit;
    Bucket bucket;
    Bandwidth limit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;

        limit = Bandwidth.classic(requestLimit,
                Refill.greedy(requestLimit, Duration.of(1, this.timeUnit.toChronoUnit())));
        bucket = Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    private static String URL
//            = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            = "https://postman-echo.com/post"; // тест клиента

    HttpClient client = HttpClient.newHttpClient();

    private DocService docService = new DocService();
    private FileService fileService = new FileService();
    private DocumentDTO documentDTO = new DocumentDTO();
    private static String sep = File.separator;
    private static final String filePath = "src" + sep + "main" + sep + "resources";
    private String documentFileName = "HonestSign";

    public static void main(String[] args) {

        String signatureSimple = "MySuperSecretSignature";

        String signature = Base64.getEncoder().encodeToString((signatureSimple).getBytes());
        System.out.println(signature);
        System.out.println("Date = "+ LocalDate.now());
        List<ProductDTO> products = new ArrayList<>();
        ProductDTO product1 = new ProductDTO(
                "СГР", // документ о сертификации (сертификат) продукта
                "2015-9-15", // дата сертификации (?) продукта
                "RU.77.99.88.003.Е.00904", // номер сертификата на продукт
                "7813562961", // ИНН владельца продукта
                "7804063927", //  ИНН производителя продукта
        "2024-1-15", // дата производства продукта
                "2783801001", // код ТН ВЭД
                "03920155", // код УКТ ВЭД
               "3562"  // код УТ ВЭДУ
        );
        products.add(product1);
        ProductDTO product2 = new ProductDTO("СГР",
                "2015-08-26",
                "RU.77.99.11.003.Е.008651.08.15",
                "7813562961",
                "7804063927",
                "2024-2-15",
                "2783801001",
                "03920155",
                "3562");
        products.add(product2);
        ProductDTO product3 = new ProductDTO("СГР",
                "2015-08-24",
                "RU.77.99.11.003.Е.008590.08.15",
                "7813562961",
                "7804063927",
                "2024-3-15",
                "2783801001",
                "03920155",
                "3562");
        products.add(product3);
        DocumentDTO documentDTO = new DocumentDTO("FigZnayetChto", "ok", "LP_INTRODUCE_GOODS",
                true, "7813562961", "7813562961",
                "7804063927", "2024-01-21", "БАД", products,
                "2024-01-23", "123456789");
        int requestLimit = 5;  // ограничение на число http запросов в единице времени
//        TimeUnit.MINUTES - интервал времени на ограниченное число запросов
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, requestLimit);
// этот блок кода для проверки работы ограничителя скорости
        for (int i = 1; i < 8; i++) {
            System.out.println("createDocument " + i);
            crptApi.createDocument(documentDTO, signature);
        }
    }

    public void createDocument(DocumentDTO documentDTO, String signature) {

        System.out.println("bucket = " + bucket.toString());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        System.out.println("probe = " + probe.toString());

        if (probe.isConsumed()) {
            String jsonDoc = docService.createDocumentJson(documentDTO);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonDoc))
                    .header("Authorization", "Basic " +
                            Base64.getEncoder().encodeToString((signature).getBytes()))
                    .build();

            HttpResponse<String> response = null;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response != null && (response.statusCode() == 200 || response.statusCode() == 201)) {
                    System.out.println("Документ создан. HTTP Status Code: " + response.statusCode());
                } else {
                    System.err.println("Ошибка при создании документа. HTTP Status Code: " + response.statusCode());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
//    в этот блок попадаем, если превышен лимит частоты запросов
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;//переводим наносекунды в секунды
            System.out.println("Превышен лимит частоты API запросов\n повторный запрос возможен через "
                    + String.valueOf(waitForRefill) + " секунд");
        }
    }

    //************************************** DocumentService *****************************************************
    public class DocService {
        private final Mapper mapper = new Mapper();
        private final FileService fileService = new FileService();

        //**********************************************************************************************************

        // Метод для создания JSON-документа на основе объекта DocumentDTO.

        public String createDocumentJson(DocumentDTO documentDto) {
            DescriptionDTO description = mapper.createDocDescription(documentDto);
//            createDocument(documentDto,sign);
            try {
                String json = new ObjectMapper().writeValueAsString(description);
                fileService.saveDocumentToFile(json, documentDto.getDoc_id());
                return json;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Ошибка при создании json.");
            }
        }
    }

    public class FileService {
        /*
         * Метод для сохранения JSON-представления документа в файл.
         * @return true, если сохранение прошло успешно, иначе false
         */
        public boolean saveDocumentToFile(String jsonDoc, String docId) {
            try {
                Files.writeString(Path.of(filePath, docId + ".json"), jsonDoc);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    //********************************** Mapper ****************************************************************************
    private class Mapper {
        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        private DescriptionDTO createDocDescription(DocumentDTO documentDTO) {
            return new DescriptionDTO(
                    new ParticipantInn(documentDTO.getParticipant_inn()),
                    documentDTO.getDoc_id(),
                    documentDTO.getDoc_status(),
                    documentDTO.getDoc_type(),
                    documentDTO.isImportRequest(),
                    documentDTO.getOwner_inn(),
                    documentDTO.getParticipant_inn(),
                    documentDTO.getProducer_inn(),
                    documentDTO.getProduction_date(),
                    documentDTO.getProduction_type(),
                    documentDTO.getProductsDTO(),
                    documentDTO.getReg_date(),
                    documentDTO.getReg_number()
            );
        }

        private DocumentDTO fromDocumentToDto(Document document) {
            return new DocumentDTO(
                    document.getId(),
                    document.getStatus(),
                    document.getProduction_type(),
                    document.isImportRequest(),
                    document.getOwner_inn(),
                    document.getParticipant_inn(),
                    document.getProducer_inn(),
                    document.getProduction_date().format(formatter),
                    document.getProduction_type(),
                    createProductDTOList(document.getProducts()),
                    document.getReg_date().format(formatter),
                    document.getReg_number());
        }


//          Метод для создания списка объектов типа ProductDTO на основе списка объектов типа Product.

        private List<ProductDTO> createProductDTOList(List<Product> productsList) {

            List<ProductDTO> productDTOList = new ArrayList<>();
            for (Product product : productsList) {
                ProductDTO productDTO = fromProductToDTO(product);
                productDTOList.add(productDTO);
            }
            return productDTOList;
        }

        //              Метод для преобразования объекта Product в объект ProductDTO
        private ProductDTO fromProductToDTO(Product product) {
            return new ProductDTO(
                    product.getCertificate_document(),
                    product.getCertificate_document_date().format(formatter),
                    product.getCertificate_document_number(),
                    product.getOwner_inn(),
                    product.getProducer_inn(),
                    product.getProduction_date().format(formatter),
                    product.getTnved_code(),
                    product.getUit_code(),
                    product.getUitu_code());
        }
    }

    //******************************************** Classes ********************************************************
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonPropertyOrder({"description", "doc_id", "doc_status", "doc_type",
            "importRequest", "owner_inn", "participant_inn", "producer_inn", "production_date",
            "production_type", "products", "reg_date", "reg_number"})
    public static class Description {
        @JsonProperty("description")
        private ParticipantInn participantInn;

        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DescriptionDTO {
        @JsonProperty("descriptionDTO")
        private ParticipantInn participantInn;

        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<ProductDTO> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DocumentDTO {
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<ProductDTO> productsDTO;
        private String reg_date;
        private String reg_number;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDTO {
        private String certificate_document; // документ о сертификации (сертификат) продукта
        private String certificate_document_date; // дата сертификации (?) продукта
        private String certificate_document_number; // номер сертификата на продукт
        private String owner_inn; // ИНН владельца продукта
        private String producer_inn; //  ИНН производителя продукта
        private String production_date; // дата производства продукта
        private String tnved_code; // код ТН ВЭД
        private String uit_code; // код УКТ ВЭД
        private String uitu_code; // код УТ ВЭДУ
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ParticipantInn {
        private String participantInn;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private String id; // id документа
        private String status; // статус документа
        private DocumentsType doc_type; // тип документа
        private boolean importRequest; // флаг запрос на импорт
        private String owner_inn; // ИНН владельца
        private String participant_inn; // ИНН участника
        private String producer_inn; // ИНН производителя
        private LocalDate production_date; // дата производства
        private String production_type; // тип производства (?)
        private List<Product> products; // инфо о продуктах
        private LocalDate reg_date; // дата регистрации документа
        private String reg_number; // регистрационный номер
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class Product {
        private String certificate_document; // документ о сертификации (сертификат) продукта
        private LocalDate certificate_document_date; // дата сертификации (?) продукта
        private String certificate_document_number; // номер сертификата на продукт
        private String owner_inn; // ИНН владельца продукта
        private String producer_inn; //  ИНН производителя продукта
        private LocalDate production_date; // дата производства продукта
        private String tnved_code; // код ТН ВЭД
        private String uit_code; // код УКТ ВЭД
        private String uitu_code; // код УТ ВЭДУ
    }

    public enum DocumentsType {
        LP_INTRODUCE_GOODS;
    }
}
