import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OilSearchListComponent, OilListConfig } from '../components/oil-search-list.component';

@Component({
  selector: 'app-essential-oils',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, OilSearchListComponent],
  template: `<app-oil-search-list [config]="oilConfig"></app-oil-search-list>`



})
export class EssentialOilsComponent implements OnInit {
  readonly oilConfig: OilListConfig = {
    title: '天然精油',
    placeholder: '輸入關鍵字搜尋精油名稱',
    emptyWithKeyword: '查無符合條件的精油',
    emptyWithoutKeyword: '查無精油資料',
    detailField: 'scentNotes',
    routePrefix: '/essential-oils',
    baseEndpoint: 'essential-oils'
  };

  constructor() {}

  ngOnInit(): void {}
}
